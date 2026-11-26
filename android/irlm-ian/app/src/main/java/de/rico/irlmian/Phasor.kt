package de.rico.irlmian

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zeigerdaten. Alle Winkel th* sind mathematisch (gegen den Uhrzeigersinn) in Grad, U L1 = 0°.
 * phi = Winkel zwischen U und I der Phase, positiv = I eilt nach (induktiv).
 */
class PhasorResult(
    val u: Array<Double?>,
    val i: Array<Double?>,
    val thU: Array<Double?>,
    val thI: Array<Double?>,
    val phi: Array<Double?>,
    val p: Array<Double?>,
    val q: Array<Double?>,
    val rotation: String?,
    val notes: List<String>,
    val hasUAngles: Boolean,
    val hasIAngles: Boolean
)

object PhasorCalc {

    fun norm180(a: Double): Double = ((a % 360.0 + 540.0) % 360.0) - 180.0
    fun norm360(a: Double): Double = ((a % 360.0) + 360.0) % 360.0

    /**
     * @param v Messwerte nach C.D.E-Schlüssel ("32.7.0" …)
     * @param clockwise true, wenn der Zähler Winkel im Uhrzeigersinn (nacheilend positiv) angibt
     */
    fun compute(v: Map<String, Double>, clockwise: Boolean): PhasorResult {
        fun m(a: Double?): Double? = a?.let { if (clockwise) -it else it }
        val notes = ArrayList<String>()

        val u = arrayOf(v["32.7.0"], v["52.7.0"], v["72.7.0"])
        val i = arrayOf(v["31.7.0"], v["51.7.0"], v["71.7.0"])

        val a1 = v["81.7.1"]; val a2 = v["81.7.2"]; val a12 = v["81.7.12"]
        val hasU = a1 != null || a2 != null
        val thU1 = 0.0
        val thU2 = m(a1) ?: 240.0
        val thU3 = m(a2) ?: (m(a12)?.let { thU2 + it } ?: 120.0)
        val thU = arrayOf<Double?>(thU1, norm360(thU2), norm360(thU3))
        if (!hasU) notes.add("Spannungswinkel fehlen im Telegramm – symmetrisches Rechtsdrehfeld angenommen.")

        val ai = arrayOf(v["81.7.4"], v["81.7.15"], v["81.7.26"])
        val thI = arrayOfNulls<Double>(3)
        val phi = arrayOfNulls<Double>(3)
        val p = arrayOfNulls<Double>(3)
        val q = arrayOfNulls<Double>(3)
        for (k in 0..2) {
            val a = m(ai[k]) ?: continue
            val tu = thU[k] ?: continue
            thI[k] = norm360(tu + a)
            val ph = norm180(tu - (tu + a))
            phi[k] = ph
            val uk = u[k]; val ik = i[k]
            if (uk != null && ik != null) {
                val rad = Math.toRadians(ph)
                p[k] = uk * ik * cos(rad)
                q[k] = uk * ik * sin(rad)
            }
        }
        val hasI = thI.any { it != null }

        var rotation: String? = null
        if (hasU) {
            val d = norm360((thU[1] ?: 240.0) - thU1)
            rotation = when {
                abs(d - 240) < 40 -> "Rechtsdrehfeld"
                abs(d - 120) < 40 -> "Linksdrehfeld"
                else -> String.format(Locale.GERMANY, "Drehfeld unklar (U L2 bei %.0f°)", d)
            }
            if (rotation == "Linksdrehfeld") notes.add("Linksdrehfeld erkannt. Falls die Anlage rechts dreht: Winkelrichtung umstellen.")
        }

        val uv = u.filterNotNull()
        if (uv.isNotEmpty()) {
            val avg = uv.average()
            for (k in 0..2) {
                val uk = u[k]
                if (uk != null && avg > 10 && uk < 0.8 * avg) notes.add("Spannung L${k + 1} deutlich niedriger als die anderen Phasen.")
            }
        }
        val iv = i.filterNotNull()
        if (iv.isNotEmpty()) {
            val imax = iv.max()
            for (k in 0..2) {
                val ik = i[k]
                if (ik != null && imax > 0.05 && ik < 0.02 * imax) notes.add("Kein nennenswerter Strom auf L${k + 1}.")
            }
        }
        val dirs = phi.mapIndexed { k, f -> if (f == null || (i[k] ?: 1.0) < 0.01) null else abs(f) > 90 }
        val lief = dirs.count { it == true }
        val bez = dirs.count { it == false }
        if (lief > 0 && bez > 0) {
            dirs.forEachIndexed { k, d ->
                if (d == true) notes.add("L${k + 1} läuft rückwärts, die anderen vorwärts – Stromwandler/Anschluss L${k + 1} prüfen.")
            }
        } else if (lief > 0) {
            notes.add("Alle Phasen in Lieferrichtung (Einspeisung).")
        }
        for (k in 0..2) {
            val f = phi[k] ?: continue
            val ff = if (abs(f) > 90) 180 - abs(f) else abs(f)
            if (ff > 60 && (i[k] ?: 0.0) > 0.01) notes.add(String.format(Locale.GERMANY, "L%d: großer Phasenwinkel (%.0f°) – cos φ = %.2f.", k + 1, f, cos(Math.toRadians(f))))
        }

        return PhasorResult(u, i, thU, thI, phi, p, q, rotation, notes, hasU, hasI)
    }
}
