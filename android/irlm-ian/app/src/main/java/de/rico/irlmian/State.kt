package de.rico.irlmian

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hält Verbindung und letzte Daten unabhängig von der Activity (z. B. beim Umschalten Hell/Dunkel). */
object State {
    private val main = Handler(Looper.getMainLooper())
    private val tf = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

    var conn: Connection? = null
    var connected = false
    var status = "Nicht verbunden"
    var demo = false

    val assembler = FrameAssembler()
    var lastApdu: Apdu? = null
    var lastFrameType = ""
    var lastError: String? = null
    var frames = 0
    var errors = 0
    var bytes = 0L
    var lastTime = 0L
    var interval = 0L
    val raw = ArrayDeque<String>()

    var listener: (() -> Unit)? = null

    private fun notifyUi() { listener?.invoke() }

    fun setStatus(text: String, ok: Boolean) {
        main.post {
            status = text
            connected = ok
            if (ok) assembler.reset()
            notifyUi()
        }
    }

    /** Wird aus dem Verbindungs-Thread aufgerufen. */
    fun onBytes(d: ByteArray) {
        main.post { handle(d) }
    }

    fun handle(d: ByteArray) {
        bytes += d.size
        raw.addFirst("[${tf.format(Date())}] ${d.size} Bytes\n${Dlms.hex(d)}")
        while (raw.size > 200) raw.removeLast()
        for ((type, apdu) in assembler.feed(d)) {
            try {
                lastApdu = Dlms.parseApdu(apdu)
                lastFrameType = type
                lastError = null
                frames++
                val now = System.currentTimeMillis()
                if (lastTime > 0) interval = now - lastTime
                lastTime = now
            } catch (e: Exception) {
                errors++
                lastError = "${e.message}\nAPDU: ${Dlms.hex(apdu)}"
            }
        }
        notifyUi()
    }

    fun clear() {
        raw.clear(); frames = 0; errors = 0; bytes = 0; lastApdu = null; lastError = null
        lastTime = 0; interval = 0; assembler.reset()
        notifyUi()
    }

    // ---------- Demo ----------
    private var demoT = 0L
    private val demoTick = object : Runnable {
        override fun run() {
            if (!demo) return
            demoT++
            handle(Demo.frame(demoT))
            main.postDelayed(this, 2000)
        }
    }

    fun startDemo() {
        if (demo) return
        demo = true
        status = "Demo-Modus (Beispieldaten)"
        connected = true
        main.post(demoTick)
    }

    fun stopDemo() {
        demo = false
        main.removeCallbacks(demoTick)
        status = "Nicht verbunden"
        connected = false
        notifyUi()
    }
}
