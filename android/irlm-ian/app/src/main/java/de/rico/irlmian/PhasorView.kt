package de.rico.irlmian

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Farben im Apple-Stil mit Avacon-Grün. */
class Pal(val dark: Boolean) {
    val bg = if (dark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
    val card = if (dark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
    val fill = if (dark) 0xFF2C2C2E.toInt() else 0xFFF2F2F7.toInt()
    val fg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    val mut = 0xFF8E8E93.toInt()
    val line = if (dark) 0xFF38383A.toInt() else 0xFFD1D1D6.toInt()
    val acc = 0xFF8CC63F.toInt()
    val accText = if (dark) 0xFF8CC63F.toInt() else 0xFF5E8F1F.toInt()
    val onAcc = 0xFF0D1A05.toInt()
    val ok = if (dark) 0xFF30D158.toInt() else 0xFF248A3D.toInt()
    val warn = if (dark) 0xFFFF9F0A.toInt() else 0xFFC93400.toInt()
    val err = if (dark) 0xFFFF453A.toInt() else 0xFFD70015.toInt()
    val l = intArrayOf(
        if (dark) 0xFFFFD60A.toInt() else 0xFFB25000.toInt(),
        if (dark) 0xFF30D158.toInt() else 0xFF248A3D.toInt(),
        if (dark) 0xFFBF5AF2.toInt() else 0xFF8944AB.toInt()
    )
}

class PhasorView(ctx: Context) : View(ctx) {
    var pal: Pal = Pal(false)
    var result: PhasorResult? = null
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dens = ctx.resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    /** Winkel mathematisch, 0° = nach oben, gegen den Uhrzeigersinn. */
    private fun px(cx: Float, len: Float, deg: Double) = cx - len * sin(Math.toRadians(deg)).toFloat()
    private fun py(cy: Float, len: Float, deg: Double) = cy - len * cos(Math.toRadians(deg)).toFloat()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h / 2
        val r = min(w, h) / 2 - 34 * dens

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1 * dens
        p.color = pal.line
        p.pathEffect = null
        c.drawCircle(cx, cy, r, p)
        p.pathEffect = DashPathEffect(floatArrayOf(4 * dens, 4 * dens), 0f)
        c.drawCircle(cx, cy, r * 0.5f, p)
        for (a in 0 until 360 step 30) {
            c.drawLine(cx, cy, px(cx, r, a.toDouble()), py(cy, r, a.toDouble()), p)
        }
        p.pathEffect = null

        val res = result
        if (res == null) {
            p.style = Paint.Style.FILL
            p.color = pal.mut
            p.textSize = 14 * dens
            p.textAlign = Paint.Align.CENTER
            c.drawText("Noch keine Daten", cx, cy, p)
            return
        }

        val umax = res.u.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val imax = res.i.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0

        for (k in 0..2) {
            val th = res.thU[k] ?: continue
            val uk = res.u[k]
            val len = if (uk == null) r else (r * (uk / umax)).toFloat().coerceAtLeast(r * 0.1f)
            arrow(c, cx, cy, len, th, pal.l[k], 3.2f * dens, "U${k + 1}", false)
        }
        for (k in 0..2) {
            val th = res.thI[k] ?: continue
            val ik = res.i[k] ?: continue
            val len = (r * 0.72 * (ik / imax)).toFloat()
            if (len < 2 * dens) continue
            arrow(c, cx, cy, len, th, pal.l[k], 2.2f * dens, "I${k + 1}", true)
        }
        p.style = Paint.Style.FILL
        p.color = pal.fg
        c.drawCircle(cx, cy, 3 * dens, p)
    }

    private fun arrow(c: Canvas, cx: Float, cy: Float, len: Float, deg: Double, color: Int, sw: Float, label: String, dashed: Boolean) {
        val ex = px(cx, len, deg)
        val ey = py(cy, len, deg)
        val dx = ex - cx
        val dy = ey - cy
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        val ux = dx / l
        val uy = dy / l
        val head = 11 * dens
        val bx = ex - ux * head
        val by = ey - uy * head

        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.strokeCap = Paint.Cap.ROUND
        p.pathEffect = if (dashed) DashPathEffect(floatArrayOf(7 * dens, 4 * dens), 0f) else null
        c.drawLine(cx, cy, bx, by, p)
        p.pathEffect = null

        p.style = Paint.Style.FILL
        val path = Path()
        path.moveTo(ex, ey)
        path.lineTo(bx - uy * head * 0.45f, by + ux * head * 0.45f)
        path.lineTo(bx + uy * head * 0.45f, by - ux * head * 0.45f)
        path.close()
        c.drawPath(path, p)

        p.textSize = 13 * dens
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        val tx = ex + ux * 14 * dens
        val ty = ey + uy * 14 * dens + 5 * dens
        c.drawText(label, tx, ty, p)
        p.isFakeBoldText = false
    }
}
