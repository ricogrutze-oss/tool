package de.rico.irlmian

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

class ObisInfo(val key: String, val name: String, val unit: String, val group: String)

/** Bekannte Größen (Auswahl nach FNN-Lastenheft iRLMSys, Tabelle 23). Schlüssel = C.D.E */
object Obis {
    private const val G_ZEIT = "Zeit"
    private const val G_MOM = "Momentanwerte"
    private const val G_WINKEL = "Phasenwinkel"
    private const val G_ENERGIE = "Zählerstände"
    private const val G_PERIODE = "Energie letzte Periode"
    private const val G_DEMAND = "Leistungsmittelwerte"
    private const val G_SONST = "Sonstiges"

    val list: List<ObisInfo> = listOf(
        ObisInfo("1.0.0", "Uhrzeit Zähler", "", G_ZEIT),
        ObisInfo("0.9.1", "Lokale Uhrzeit", "", G_ZEIT),
        ObisInfo("0.9.2", "Lokales Datum", "", G_ZEIT),

        ObisInfo("32.7.0", "Spannung L1", "V", G_MOM),
        ObisInfo("52.7.0", "Spannung L2", "V", G_MOM),
        ObisInfo("72.7.0", "Spannung L3", "V", G_MOM),
        ObisInfo("31.7.0", "Strom L1", "A", G_MOM),
        ObisInfo("51.7.0", "Strom L2", "A", G_MOM),
        ObisInfo("71.7.0", "Strom L3", "A", G_MOM),
        ObisInfo("91.7.3", "Neutralleiterstrom (berechnet)", "A", G_MOM),
        ObisInfo("91.7.0", "Neutralleiterstrom", "A", G_MOM),
        ObisInfo("14.7.0", "Netzfrequenz", "Hz", G_MOM),
        ObisInfo("16.7.0", "Wirkleistung gesamt", "W", G_MOM),
        ObisInfo("15.7.0", "Wirkleistung gesamt |P|", "W", G_MOM),
        ObisInfo("36.7.0", "Wirkleistung L1", "W", G_MOM),
        ObisInfo("56.7.0", "Wirkleistung L2", "W", G_MOM),
        ObisInfo("76.7.0", "Wirkleistung L3", "W", G_MOM),
        ObisInfo("1.7.0", "Wirkleistung Bezug +P", "W", G_MOM),
        ObisInfo("2.7.0", "Wirkleistung Lieferung −P", "W", G_MOM),
        ObisInfo("3.7.0", "Blindleistung +Q", "var", G_MOM),
        ObisInfo("4.7.0", "Blindleistung −Q", "var", G_MOM),
        ObisInfo("5.7.0", "Blindleistung Q1", "var", G_MOM),
        ObisInfo("6.7.0", "Blindleistung Q2", "var", G_MOM),
        ObisInfo("7.7.0", "Blindleistung Q3", "var", G_MOM),
        ObisInfo("8.7.0", "Blindleistung Q4", "var", G_MOM),
        ObisInfo("9.7.0", "Scheinleistung +S", "VA", G_MOM),
        ObisInfo("10.7.0", "Scheinleistung −S", "VA", G_MOM),
        ObisInfo("13.7.0", "Leistungsfaktor gesamt", "", G_MOM),
        ObisInfo("33.7.0", "Leistungsfaktor L1", "", G_MOM),
        ObisInfo("53.7.0", "Leistungsfaktor L2", "", G_MOM),
        ObisInfo("73.7.0", "Leistungsfaktor L3", "", G_MOM),

        ObisInfo("81.7.1", "Winkel U L2 zu U L1", "°", G_WINKEL),
        ObisInfo("81.7.2", "Winkel U L3 zu U L1", "°", G_WINKEL),
        ObisInfo("81.7.12", "Winkel U L3 zu U L2", "°", G_WINKEL),
        ObisInfo("81.7.4", "Winkel I L1 zu U L1", "°", G_WINKEL),
        ObisInfo("81.7.15", "Winkel I L2 zu U L2", "°", G_WINKEL),
        ObisInfo("81.7.26", "Winkel I L3 zu U L3", "°", G_WINKEL),

        ObisInfo("1.8.0", "Wirkenergie Bezug +A", "Wh", G_ENERGIE),
        ObisInfo("2.8.0", "Wirkenergie Lieferung −A", "Wh", G_ENERGIE),
        ObisInfo("5.8.0", "Blindenergie Q1 (+Ri)", "varh", G_ENERGIE),
        ObisInfo("6.8.0", "Blindenergie Q2 (+Rc)", "varh", G_ENERGIE),
        ObisInfo("7.8.0", "Blindenergie Q3 (−Ri)", "varh", G_ENERGIE),
        ObisInfo("8.8.0", "Blindenergie Q4 (−Rc)", "varh", G_ENERGIE),
        ObisInfo("21.8.0", "Wirkenergie Bezug L1", "Wh", G_ENERGIE),
        ObisInfo("41.8.0", "Wirkenergie Bezug L2", "Wh", G_ENERGIE),
        ObisInfo("61.8.0", "Wirkenergie Bezug L3", "Wh", G_ENERGIE),
        ObisInfo("22.8.0", "Wirkenergie Lieferung L1", "Wh", G_ENERGIE),
        ObisInfo("42.8.0", "Wirkenergie Lieferung L2", "Wh", G_ENERGIE),
        ObisInfo("62.8.0", "Wirkenergie Lieferung L3", "Wh", G_ENERGIE),

        ObisInfo("1.29.0", "Wirkenergie Bezug letzte Periode", "Wh", G_PERIODE),
        ObisInfo("2.29.0", "Wirkenergie Lieferung letzte Periode", "Wh", G_PERIODE),
        ObisInfo("5.29.0", "Blindenergie R1 letzte Periode", "varh", G_PERIODE),
        ObisInfo("6.29.0", "Blindenergie R2 letzte Periode", "varh", G_PERIODE),
        ObisInfo("7.29.0", "Blindenergie R3 letzte Periode", "varh", G_PERIODE),
        ObisInfo("8.29.0", "Blindenergie R4 letzte Periode", "varh", G_PERIODE),

        ObisInfo("1.4.0", "Mittelwert laufend Bezug", "W", G_DEMAND),
        ObisInfo("1.5.0", "Letzter Mittelwert Bezug", "W", G_DEMAND),
        ObisInfo("2.4.0", "Mittelwert laufend Lieferung", "W", G_DEMAND),
        ObisInfo("2.5.0", "Letzter Mittelwert Lieferung", "W", G_DEMAND),
        ObisInfo("5.4.0", "Mittelwert laufend R1", "var", G_DEMAND),
        ObisInfo("6.4.0", "Mittelwert laufend R2", "var", G_DEMAND),
        ObisInfo("7.4.0", "Mittelwert laufend R3", "var", G_DEMAND),
        ObisInfo("8.4.0", "Mittelwert laufend R4", "var", G_DEMAND),
        ObisInfo("5.5.0", "Letzter Mittelwert R1", "var", G_DEMAND),
        ObisInfo("6.5.0", "Letzter Mittelwert R2", "var", G_DEMAND),
        ObisInfo("7.5.0", "Letzter Mittelwert R3", "var", G_DEMAND),
        ObisInfo("8.5.0", "Letzter Mittelwert R4", "var", G_DEMAND),

        ObisInfo("25.9.0", "Push-Setup (Kennung)", "", G_SONST),
        ObisInfo("96.1.0", "Gerätenummer", "", G_SONST),
        ObisInfo("96.2.15", "Prüfmodus-Status", "", G_SONST)
    )

    val groups = listOf(G_MOM, G_WINKEL, G_ENERGIE, G_PERIODE, G_DEMAND, G_ZEIT, G_SONST)

    private val byKey = list.associateBy { it.key }

    fun info(cde: String?): ObisInfo? = cde?.let { byKey[it] }

    /** "1-0:32.7.0.255" -> "32.7.0" */
    fun cde(obis: String): String = obis.substringAfter(':').split('.').take(3).joinToString(".")

    /** "1-0:32.7.0.255" -> "1-0:32.7.0" */
    fun short(obis: String): String = obis.split('.').take(3).joinToString(".")

    fun unitName(code: Int): String = when (code) {
        1 -> "a"; 2 -> "mo"; 3 -> "Wo"; 4 -> "d"; 5 -> "h"; 6 -> "min"; 7 -> "s"
        8 -> "°"; 9 -> "°C"; 27 -> "W"; 28 -> "VA"; 29 -> "var"; 30 -> "Wh"; 31 -> "VAh"
        32 -> "varh"; 33 -> "A"; 35 -> "V"; 44 -> "Hz"; 56 -> "%"; 255 -> ""
        else -> "[$code]"
    }

    private val prefixUnits = setOf("W", "VA", "var", "Wh", "VAh", "varh")

    fun format(v: Double?, unit: String): String {
        if (v == null) return "–"
        var x = v
        var u = unit
        if (u in prefixUnits) {
            if (abs(x) >= 1e6) { x /= 1e6; u = "M$u" } else if (abs(x) >= 1e3) { x /= 1e3; u = "k$u" }
        }
        val a = abs(x)
        val dec = when {
            u == "°" -> 1
            u == "Hz" -> 2
            a == 0.0 -> 0
            a < 10 -> 3
            a < 100 -> 2
            a < 1000 -> 1
            else -> 0
        }
        val s = String.format(Locale.GERMANY, "%,.${dec}f", x)
        return if (u.isEmpty()) s else "$s $u"
    }

    fun pow10(s: Int): Double = 10.0.pow(s)
}
