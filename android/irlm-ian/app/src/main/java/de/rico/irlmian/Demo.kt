package de.rico.irlmian

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/** Erzeugt beispielhafte DLMS-Push-Telegramme (für den Demo-Modus und die Tests). */
object Demo {

    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun obis(c: Int, d: Int, e: Int, a: Int = 1, bb: Int = 0) = b(9, 6, a, bb, c, d, e, 255)
    private fun u16(v: Int) = b(18, v shr 8, v)
    private fun i16(v: Int) = b(16, v shr 8, v)
    private fun u32(v: Long) = b(6, (v shr 24).toInt(), (v shr 16).toInt(), (v shr 8).toInt(), v.toInt())
    private fun i32(v: Int) = b(5, v shr 24, v shr 16, v shr 8, v)
    private fun su(scaler: Int, unit: Int) = b(2, 2, 15, scaler, 22, unit)

    private fun entry(o: ByteArray, value: ByteArray, scaler: Int, unit: Int) =
        b(2, 3) + o + value + su(scaler, unit)

    private fun wrap(apdu: ByteArray): ByteArray =
        b(0, 1, 0, 1, 0, 1, apdu.size shr 8, apdu.size) + apdu

    /** Telegramm mit OBIS-Kennzahlen und Skalierern (so wie viele Zähler es senden). */
    fun frame(t: Long): ByteArray {
        val w = t / 2.0
        val u = doubleArrayOf(230.4 + sin(w) * 0.6, 231.1 + cos(w * 0.7) * 0.5, 229.8 + sin(w * 1.3) * 0.4)
        val i = doubleArrayOf(42.3 + sin(w * 0.5) * 2, 38.9 + cos(w * 0.4) * 2, 40.6 + sin(w * 0.3) * 2)
        val phi = doubleArrayOf(18.0 + sin(w) * 2, 22.0, 15.0)        // I eilt nach (induktiv)
        val pk = DoubleArray(3) { u[it] * i[it] * cos(Math.toRadians(phi[it])) }
        val qk = DoubleArray(3) { u[it] * i[it] * sin(Math.toRadians(phi[it])) }
        val p = pk.sum()
        val q = qk.sum()
        val s = Math.hypot(p, q)

        val list = ArrayList<ByteArray>()
        list.add(b(9, 6, 0, 0, 25, 9, 0, 255))   // Kennung des Push-Setups
        for (k in 0..2) {
            list.add(entry(obis(32 + 20 * k, 7, 0), u16((u[k] * 10).roundToInt()), -1, 35))
            list.add(entry(obis(31 + 20 * k, 7, 0), u32((i[k] * 1000).roundToLong()), -3, 33))
        }
        list.add(entry(obis(14, 7, 0), u16(5001), -2, 44))
        list.add(entry(obis(81, 7, 1), u16(2400), -1, 8))
        list.add(entry(obis(81, 7, 2), u16(1200), -1, 8))
        // mathematische Winkelrichtung: nacheilender Strom = 360° − φ
        list.add(entry(obis(81, 7, 4), u16(((360 - phi[0]) * 10).roundToInt()), -1, 8))
        list.add(entry(obis(81, 7, 15), u16(((360 - phi[1]) * 10).roundToInt()), -1, 8))
        list.add(entry(obis(81, 7, 26), u16(((360 - phi[2]) * 10).roundToInt()), -1, 8))
        list.add(entry(obis(1, 7, 0), i32(p.roundToInt()), 0, 27))
        list.add(entry(obis(2, 7, 0), i32(0), 0, 27))
        list.add(entry(obis(5, 7, 0), i32(q.roundToInt()), 0, 29))
        list.add(entry(obis(13, 7, 0), i16((p / s * 1000).roundToInt()), -3, 255))
        list.add(entry(obis(1, 8, 0), u32(1234567L + t * 15), 1, 30))
        list.add(entry(obis(2, 8, 0), u32(2345L), 1, 30))
        list.add(entry(obis(5, 8, 0), u32(345678L + t * 4), 1, 32))
        list.add(entry(obis(8, 8, 0), u32(1234L), 1, 32))
        list.add(entry(obis(1, 29, 0), u32(41234L), 0, 30))
        list.add(entry(obis(1, 5, 0), u32(164800L), 0, 27))

        var body = b(2, list.size)
        list.forEach { body += it }
        val apdu = b(0x0F, 0, 0, 0, 1, 0) + body
        return wrap(apdu)
    }

    /** Telegramm ohne Kennzahlen – nur Werte, wie es die Push-Objektliste vorgibt. */
    fun frameValuesOnly(): ByteArray {
        var body = b(2, 4)
        body += u16(2304)
        body += u16(2311)
        body += u16(2298)
        body += u32(42300)
        val apdu = b(0x0F, 0, 0, 0, 2, 0) + body
        return wrap(apdu)
    }
}
