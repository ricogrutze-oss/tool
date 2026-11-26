package de.rico.irlmian

/** Ein dekodierter A-XDR-Datenknoten (DLMS "Data"). */
class DNode(
    val tag: Int,
    val value: Any?,
    val children: List<DNode> = emptyList(),
    val bytes: ByteArray? = null
) {
    val typeName: String get() = Dlms.typeName(tag)
    val isContainer: Boolean get() = tag == 1 || tag == 2
    fun asDouble(): Double? = (value as? Number)?.toDouble()
}

class DlmsException(msg: String) : Exception(msg)

/** Ergebnis einer dekodierten Anwendungsnachricht (APDU). */
class Apdu(
    val kind: String,
    val time: String?,
    val data: DNode?,
    val info: String? = null,
    val obis: String? = null
)

object Dlms {

    fun typeName(tag: Int): String = when (tag) {
        0 -> "null"; 1 -> "array"; 2 -> "structure"; 3 -> "boolean"; 4 -> "bit-string"
        5 -> "int32"; 6 -> "uint32"; 9 -> "octet-string"; 10 -> "visible-string"
        12 -> "utf8-string"; 13 -> "bcd"; 15 -> "int8"; 16 -> "int16"; 17 -> "uint8"
        18 -> "uint16"; 20 -> "int64"; 21 -> "uint64"; 22 -> "enum"; 23 -> "float32"
        24 -> "float64"; 25 -> "date-time"; 26 -> "date"; 27 -> "time"
        else -> String.format("Typ 0x%02X", tag)
    }

    class Reader(val b: ByteArray, var p: Int = 0) {
        fun u8(): Int {
            if (p >= b.size) throw DlmsException("Datenende bei Position $p")
            return b[p++].toInt() and 0xFF
        }

        fun bytes(n: Int): ByteArray {
            if (n < 0 || p + n > b.size) throw DlmsException("Länge $n passt nicht (Position $p)")
            val r = b.copyOfRange(p, p + n)
            p += n
            return r
        }

        fun len(): Int {
            val f = u8()
            if (f < 0x80) return f
            val n = f and 0x7F
            if (n == 0 || n > 4) throw DlmsException("Ungültige Längenangabe")
            var v = 0
            repeat(n) { v = (v shl 8) or u8() }
            return v
        }

        fun uN(n: Int): Long {
            var v = 0L
            repeat(n) { v = (v shl 8) or u8().toLong() }
            return v
        }
    }

    fun parseData(r: Reader, depth: Int = 0): DNode {
        if (depth > 40) throw DlmsException("Zu tief verschachtelt")
        val tag = r.u8()
        return when (tag) {
            0 -> DNode(0, null)
            1, 2 -> {
                val n = r.len()
                val ch = ArrayList<DNode>()
                repeat(n) { ch.add(parseData(r, depth + 1)) }
                DNode(tag, null, ch)
            }
            3 -> DNode(3, if (r.u8() != 0) 1 else 0)
            4 -> {
                val bits = r.len()
                DNode(4, null, bytes = r.bytes((bits + 7) / 8))
            }
            5 -> DNode(5, r.uN(4).toInt())
            6 -> DNode(6, r.uN(4))
            9 -> {
                val n = r.len()
                DNode(9, null, bytes = r.bytes(n))
            }
            10, 12 -> {
                val n = r.len()
                val bs = r.bytes(n)
                DNode(tag, String(bs, Charsets.UTF_8), bytes = bs)
            }
            13 -> DNode(13, r.u8())
            15 -> DNode(15, r.u8().toByte().toInt())
            16 -> DNode(16, r.uN(2).toInt().toShort().toInt())
            17 -> DNode(17, r.u8())
            18 -> DNode(18, r.uN(2).toInt())
            20 -> DNode(20, r.uN(8))
            21 -> {
                val v = r.uN(8)
                DNode(21, if (v < 0) v.toULong().toDouble() else v)
            }
            22 -> DNode(22, r.u8())
            23 -> DNode(23, java.lang.Float.intBitsToFloat(r.uN(4).toInt()).toDouble())
            24 -> DNode(24, java.lang.Double.longBitsToDouble(r.uN(8)))
            25 -> DNode(25, null, bytes = r.bytes(12))
            26 -> DNode(26, null, bytes = r.bytes(5))
            27 -> DNode(27, null, bytes = r.bytes(4))
            else -> throw DlmsException(String.format("Unbekannter Datentyp 0x%02X an Position %d", tag, r.p - 1))
        }
    }

    fun parseApdu(a: ByteArray): Apdu {
        if (a.isEmpty()) throw DlmsException("Leere Nachricht")
        val r = Reader(a)
        val t = r.u8()
        return when (t) {
            0x0F -> {
                val inv = r.uN(4)
                var time: String? = null
                val l = r.u8()
                if (l == 0x09) {
                    time = dateTime(r.bytes(r.len()))
                } else if (l != 0) {
                    time = dateTime(r.bytes(l))
                }
                val d = parseData(r)
                Apdu("Data-Notification", time, d, String.format("Invoke-ID 0x%08X", inv))
            }
            0xC2 -> {
                var time: String? = null
                if (r.u8() != 0) time = dateTime(r.bytes(r.len()))
                val cls = r.uN(2)
                val ob = obis(r.bytes(6))
                val attr = r.u8()
                val d = parseData(r)
                Apdu("Event-Notification", time, d, "Klasse $cls, Attribut $attr", ob)
            }
            0xD9, 0xDA, 0xDB, 0xDD, 0xDE, 0xDF, 0xE0 ->
                Apdu(String.format("Verschlüsselt/Block (0x%02X)", t), null, null,
                    "Verschlüsselte oder blockweise Übertragung wird noch nicht unterstützt")
            else -> Apdu(String.format("Unbekannt (0x%02X)", t), null, null)
        }
    }

    fun obis(b: ByteArray): String {
        val v = b.map { it.toInt() and 0xFF }
        return "${v[0]}-${v[1]}:${v[2]}.${v[3]}.${v[4]}.${v[5]}"
    }

    fun dateTime(b: ByteArray): String {
        if (b.size < 8) return hex(b)
        val y = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
        fun f(i: Int): String {
            val v = b[i].toInt() and 0xFF
            return if (v == 0xFF) "--" else String.format("%02d", v)
        }
        val yy = if (y == 0xFFFF) "----" else y.toString()
        return "${f(3)}.${f(2)}.$yy ${f(5)}:${f(6)}:${f(7)}"
    }

    fun hex(b: ByteArray): String = b.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }

    /** Lesbare Baumdarstellung für die Rohdaten-Ansicht. */
    fun tree(n: DNode, indent: String = "", sb: StringBuilder = StringBuilder()): String {
        sb.append(indent)
        when {
            n.isContainer -> {
                sb.append(n.typeName).append(" [").append(n.children.size).append("]\n")
                n.children.forEach { tree(it, "$indent  ", sb) }
                return sb.toString()
            }
            n.tag == 9 && n.bytes != null && n.bytes.size == 6 ->
                sb.append("octet-string  ").append(obis(n.bytes)).append("  (OBIS?)")
            n.tag == 9 && n.bytes != null && n.bytes.size == 12 ->
                sb.append("octet-string  ").append(dateTime(n.bytes)).append("  (Zeit?)")
            n.tag == 25 && n.bytes != null -> sb.append("date-time  ").append(dateTime(n.bytes))
            n.bytes != null && n.value == null -> sb.append(n.typeName).append("  ").append(hex(n.bytes))
            else -> sb.append(n.typeName).append("  ").append(n.value.toString())
        }
        sb.append('\n')
        return sb.toString()
    }
}

/**
 * Zerlegt den TCP-Datenstrom in einzelne APDUs.
 * Unterstützt DLMS-Wrapper (IEC 62056-47, Version 0x0001), HDLC-Rahmen (0x7E) und rohe APDUs.
 */
class FrameAssembler {
    private var buf = ByteArray(0)

    fun reset() { buf = ByteArray(0) }

    private fun u(i: Int) = buf[i].toInt() and 0xFF

    private fun isStart(i: Int): Boolean {
        val b = buf[i].toInt() and 0xFF
        if (b == 0x7E || b == 0x0F) return true
        return b == 0x00 && i + 1 < buf.size && (buf[i + 1].toInt() and 0xFF) == 0x01
    }

    fun feed(d: ByteArray): List<Pair<String, ByteArray>> {
        buf += d
        val out = ArrayList<Pair<String, ByteArray>>()
        while (buf.isNotEmpty()) {
            val b0 = u(0)
            if (b0 == 0x00 && buf.size >= 2 && u(1) == 0x01) {
                if (buf.size < 8) break
                val len = (u(6) shl 8) or u(7)
                if (buf.size < 8 + len) break
                out.add("WPDU" to buf.copyOfRange(8, 8 + len))
                buf = buf.copyOfRange(8 + len, buf.size)
            } else if (b0 == 0x00 && buf.size < 2) {
                break
            } else if (b0 == 0x7E) {
                if (buf.size < 3) break
                val flen = ((u(1) and 0x07) shl 8) or u(2)
                if (flen < 7) {
                    buf = buf.copyOfRange(1, buf.size); continue
                }
                if (buf.size < flen + 2) break
                val frame = buf.copyOfRange(1, 1 + flen)
                buf = buf.copyOfRange(flen + 2, buf.size)
                hdlcInfo(frame)?.let { out.add("HDLC" to it) }
            } else if (b0 == 0x0F || b0 == 0xC2) {
                out.add("roh" to buf)
                buf = ByteArray(0)
            } else {
                var i = 1
                while (i < buf.size && !isStart(i)) i++
                buf = buf.copyOfRange(i, buf.size)
            }
        }
        if (buf.size > 65536) buf = ByteArray(0)
        return out
    }

    private fun hdlcInfo(f: ByteArray): ByteArray? {
        var p = 2
        while (p < f.size && (f[p].toInt() and 1) == 0) p++
        p++
        while (p < f.size && (f[p].toInt() and 1) == 0) p++
        p++
        p++ // Control
        if (p + 2 >= f.size - 2) return null
        p += 2 // HCS
        var info = f.copyOfRange(p, f.size - 2)
        if (info.size >= 3 && (info[0].toInt() and 0xFF) == 0xE6 &&
            ((info[1].toInt() and 0xFF) == 0xE7 || (info[1].toInt() and 0xFF) == 0xE6)
        ) info = info.copyOfRange(3, info.size)
        return if (info.isEmpty()) null else info
    }
}
