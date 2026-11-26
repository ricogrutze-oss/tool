package de.rico.irlmian

/** Ein angezeigter Messwert. key = OBIS ohne F ("1-0:32.7.0") oder "#n" bei Werten ohne Kennzahl. */
class Reading(
    val key: String,
    val obis: String?,
    val index: Int,
    val name: String,
    val raw: Double?,
    val scaler: Int,
    val unit: String,
    val text: String?,
    val group: String,
    val mapped: Boolean
) {
    val cde: String? get() = obis?.let { Obis.cde(it) }
    val value: Double? get() = raw?.let { it * Obis.pow10(scaler) }
    val display: String get() = text ?: Obis.format(value, unit)
}

/** Benutzer-Zuordnung: für Werte ohne OBIS eine Kennzahl, und für jeden Wert optional ein fester Skalierer. */
interface Mapping {
    fun obisFor(key: String): String?
    fun scalerFor(key: String): Int?
}

object NoMapping : Mapping {
    override fun obisFor(key: String): String? = null
    override fun scalerFor(key: String): Int? = null
}

object Interpreter {

    private val numericTags = setOf(3, 5, 6, 13, 15, 16, 17, 18, 20, 21, 22, 23, 24)

    private fun isObis(n: DNode) = n.tag == 9 && n.bytes?.size == 6
    private fun isScalerUnit(n: DNode) =
        n.tag == 2 && n.children.size == 2 && n.children[0].tag == 15 && n.children[1].tag == 22

    private fun isValue(n: DNode) = n.tag in numericTags || n.tag == 9 || n.tag == 10 ||
        n.tag == 12 || n.tag == 25 || n.tag == 26 || n.tag == 27 || n.tag == 4

    fun interpret(root: DNode, map: Mapping = NoMapping): List<Reading> {
        val out = ArrayList<Reading>()
        var idx = 0

        fun add(n: DNode, obisFull: String?, su: Pair<Int, Int>?) {
            idx++
            val key = obisFull?.let { Obis.short(it) } ?: "#$idx"
            var obis = obisFull
            var mapped = false
            if (obis == null) {
                map.obisFor(key)?.let { obis = "1-0:$it.255"; mapped = true }
            }
            val info = Obis.info(obis?.let { Obis.cde(it) })
            var text: String? = null
            var raw: Double? = n.asDouble()
            val bs = n.bytes
            if (n.tag == 25 && bs != null) text = Dlms.dateTime(bs)
            else if (n.tag == 9 && bs != null) {
                text = when (bs.size) {
                    12 -> Dlms.dateTime(bs)
                    6 -> Dlms.obis(bs)
                    else -> if (bs.all { it in 32..126 }) String(bs, Charsets.US_ASCII) else Dlms.hex(bs)
                }
            } else if ((n.tag == 10 || n.tag == 12) && n.value != null) text = n.value.toString()
            else if (bs != null && raw == null) text = Dlms.hex(bs)
            if (text != null) raw = null
            val unit = su?.let { Obis.unitName(it.second) } ?: info?.unit ?: ""
            val scaler = map.scalerFor(key) ?: su?.first ?: 0
            val name = info?.name ?: obis?.let { "OBIS " + Obis.short(it) } ?: "Wert $idx"
            val group = info?.group ?: if (obis == null) "Ohne Kennzahl" else "Sonstiges"
            out.add(Reading(key, obis, idx, name, raw, scaler, unit, text, group, mapped))
        }

        fun walk(n: DNode) {
            if (!n.isContainer) {
                if (isValue(n)) add(n, null, null)
                return
            }
            val ch = n.children
            var i = 0
            while (i < ch.size) {
                val c = ch[i]
                if (isObis(c)) {
                    val ob = Dlms.obis(c.bytes!!)
                    var j = i + 1
                    var valueNode: DNode? = null
                    var su: Pair<Int, Int>? = null
                    while (j < ch.size && !isObis(ch[j])) {
                        val x = ch[j]
                        if (isScalerUnit(x)) {
                            su = (x.children[0].value as Int) to (x.children[1].value as Int)
                        } else if (valueNode == null && !x.isContainer && isValue(x)) {
                            valueNode = x
                        } else if (x.isContainer) {
                            walk(x)
                        }
                        j++
                    }
                    if (valueNode != null) add(valueNode, ob, su)
                    // OBIS ohne Wert (z. B. Kennung des Push-Setups) wird übersprungen
                    i = j
                } else if (isScalerUnit(c) && out.isNotEmpty()) {
                    // Skalierer direkt nach einem Wert ohne Kennzahl: auf den letzten Wert anwenden
                    val last = out.removeAt(out.size - 1)
                    val s = c.children[0].value as Int
                    val u = Obis.unitName(c.children[1].value as Int)
                    out.add(Reading(last.key, last.obis, last.index, last.name, last.raw,
                        map.scalerFor(last.key) ?: s, u.ifEmpty { last.unit }, last.text, last.group, last.mapped))
                    i++
                } else if (c.isContainer) {
                    walk(c); i++
                } else {
                    if (isValue(c)) add(c, null, null)
                    i++
                }
            }
        }

        walk(root)
        return out
    }
}
