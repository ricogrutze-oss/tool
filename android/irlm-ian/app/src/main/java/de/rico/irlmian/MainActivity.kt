package de.rico.irlmian

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.math.BigDecimal
import java.util.Locale

class MainActivity : Activity(), Mapping {

    private lateinit var pal: Pal
    private lateinit var prefs: SharedPreferences
    private var dens = 1f
    private fun dp(v: Int) = (v * dens).toInt()

    private lateinit var pill: TextView
    private lateinit var statusTv: TextView
    private lateinit var hostEt: EditText
    private lateinit var portEt: EditText
    private lateinit var serverCb: CheckBox
    private lateinit var connectBtn: Button
    private lateinit var scanBtn: Button
    private lateinit var demoBtn: Button
    private lateinit var scanInfo: TextView
    private lateinit var scanList: LinearLayout
    private val tabs = ArrayList<LinearLayout>()
    private val segBtns = ArrayList<TextView>()
    private var tab = 0
    private lateinit var infoTv: TextView
    private lateinit var kpiBox: LinearLayout
    private lateinit var valuesBox: LinearLayout
    private lateinit var phasorView: PhasorView
    private lateinit var phasorTable: LinearLayout
    private lateinit var notesBox: LinearLayout
    private lateinit var convBtn: Button
    private lateinit var rawInfo: TextView
    private lateinit var treeTv: TextView
    private lateinit var rawTv: TextView
    private var scanner: Scanner? = null

    // ---------- Zuordnung (Mapping) ----------
    override fun obisFor(key: String): String? = prefs.getString("obis_$key", null)
    override fun scalerFor(key: String): Int? =
        if (prefs.contains("sc_$key")) prefs.getInt("sc_$key", 0) else null

    private val clockwise: Boolean get() = prefs.getBoolean("cw", false)

    // ---------- Lebenszyklus ----------
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ian", MODE_PRIVATE)
        dens = resources.displayMetrics.density
        val mode = prefs.getInt("theme", 0)
        val sysDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        pal = Pal(when (mode) { 1 -> false; 2 -> true; else -> sysDark })
        tab = prefs.getInt("tab", 0)

        window.statusBarColor = pal.card
        window.navigationBarColor = pal.bg
        if (!pal.dark) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        setContentView(buildUi())
        State.listener = { render() }
        if (State.conn != null) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (State.listener != null) State.listener = null
        scanner?.cancelled = true
    }

    // ---------- UI-Bausteine ----------
    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * dens
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun tv(text: String, size: Float = 15f, color: Int = pal.fg, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(pal.card, 14)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = lp(MP, WC).apply { bottomMargin = dp(12) }
    }

    private fun button(text: String, primary: Boolean = false) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        stateListAnimator = null
        background = rounded(if (primary) pal.acc else pal.fill, 10)
        setTextColor(if (primary) pal.onAcc else pal.accText)
        if (primary) typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(10), dp(12), dp(10))
        minHeight = dp(44)
        minimumHeight = dp(44)
    }

    private fun edit(hint: String) = EditText(this).apply {
        this.hint = hint
        background = rounded(pal.fill, 10)
        setTextColor(pal.fg)
        setHintTextColor(pal.mut)
        textSize = 16f
        isSingleLine = true
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun section(text: String) = tv(text.uppercase(Locale.GERMANY), 12f, pal.mut).apply {
        setPadding(dp(16), dp(10), dp(16), dp(6))
    }

    private fun sep() = View(this).apply {
        setBackgroundColor(pal.line)
        layoutParams = lp(MP, maxOf(1, dp(1) / 2))
    }

    private fun mono(tv: TextView) {
        tv.typeface = Typeface.MONOSPACE
        tv.textSize = 11.5f
        tv.setTextIsSelectable(true)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- Aufbau ----------
    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
        }

        // Kopfzeile
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(pal.card)
            setPadding(dp(16), dp(12), dp(8), dp(12))
        }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(tv("IAN-Monitor", 22f, pal.fg, true))
        titleCol.addView(tv("iRLM-Zähler · DLMS-Push · v${BuildConfig.VERSION_NAME}", 12f, pal.mut))
        head.addView(titleCol, lp(0, WC, 1f))
        pill = tv("", 12f, pal.mut, true).apply { setPadding(dp(10), dp(4), dp(10), dp(4)) }
        head.addView(pill)
        val themeBtn = tv("◐", 22f, pal.accText).apply {
            setPadding(dp(14), dp(4), dp(10), dp(4))
            setOnClickListener {
                val m = (prefs.getInt("theme", 0) + 1) % 3
                prefs.edit().putInt("theme", m).apply()
                toast(arrayOf("Design: wie System", "Design: hell", "Design: dunkel")[m])
                recreate()
            }
        }
        head.addView(themeBtn)
        root.addView(head, lp(MP, WC))
        root.addView(sep())

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(40))
        }
        scroll.addView(col, ViewGroup.LayoutParams(MP, WC))
        root.addView(scroll, lp(MP, 0, 1f))

        // Verbindung
        val conn = card()
        conn.addView(tv("Verbindung", 17f, pal.fg, true))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hostEt = edit("IP des Zählers").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("host", ""))
        }
        portEt = edit("Port").apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getString("port", "4059"))
        }
        row.addView(hostEt, lp(0, WC, 1f).apply { rightMargin = dp(8) })
        row.addView(portEt, lp(dp(96), WC))
        conn.addView(row, lp(MP, WC).apply { topMargin = dp(10) })

        serverCb = CheckBox(this).apply {
            text = "Server-Modus (Zähler baut die Verbindung selbst auf)"
            setTextColor(pal.fg)
            textSize = 14f
            buttonTintList = android.content.res.ColorStateList.valueOf(pal.acc)
            isChecked = prefs.getBoolean("server", false)
        }
        conn.addView(serverCb, lp(MP, WC).apply { topMargin = dp(6) })

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scanBtn = button("Zähler suchen").apply { setOnClickListener { scan() } }
        connectBtn = button("Verbinden", true).apply { setOnClickListener { toggleConnect() } }
        btnRow.addView(scanBtn, lp(0, WC, 1f).apply { rightMargin = dp(8) })
        btnRow.addView(connectBtn, lp(0, WC, 1f))
        conn.addView(btnRow, lp(MP, WC).apply { topMargin = dp(8) })

        statusTv = tv("", 13f, pal.mut).apply { setPadding(0, dp(8), 0, 0) }
        conn.addView(statusTv)
        scanInfo = tv("", 13f, pal.mut).apply { setPadding(0, dp(4), 0, 0) }
        conn.addView(scanInfo)
        scanList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        conn.addView(scanList)
        demoBtn = button("Demo-Daten anzeigen").apply {
            background = null
            textSize = 14f
            setOnClickListener {
                if (State.demo) State.stopDemo() else {
                    State.conn?.stop(); State.conn = null
                    State.clear(); State.startDemo()
                }
                render()
            }
        }
        conn.addView(demoBtn, lp(WC, WC).apply { gravity = Gravity.START })
        col.addView(conn)

        // Segment-Umschalter
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(if (pal.dark) pal.card else 0xFFE3E3E8.toInt(), 10)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        listOf("Werte", "Zeiger", "Rohdaten").forEachIndexed { i, s ->
            val b = tv(s, 14f, pal.fg, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, dp(7))
                setOnClickListener { selectTab(i) }
            }
            segBtns.add(b)
            seg.addView(b, lp(0, WC, 1f))
        }
        col.addView(seg, lp(MP, WC).apply { bottomMargin = dp(12) })

        // Tab Werte
        val tv0 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        infoTv = tv("", 13f, pal.mut).apply { setPadding(dp(4), 0, dp(4), dp(8)) }
        tv0.addView(infoTv)
        kpiBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tv0.addView(kpiBox)
        valuesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tv0.addView(valuesBox)
        tabs.add(tv0)

        // Tab Zeiger
        val tv1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pc = card()
        phasorView = PhasorView(this).also { it.pal = pal }
        pc.addView(phasorView, lp(MP, WC))
        pc.addView(tv("Durchgezogen = Spannung, gestrichelt = Strom · L1 · L2 · L3 farbig", 12f, pal.mut).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(4))
        })
        tv1.addView(pc)
        val tc = card()
        phasorTable = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(phasorTable)
        tv1.addView(tc)
        val nc = card()
        notesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nc.addView(notesBox)
        convBtn = button("").apply {
            setOnClickListener {
                prefs.edit().putBoolean("cw", !clockwise).apply()
                render()
            }
        }
        nc.addView(convBtn, lp(MP, WC).apply { topMargin = dp(10) })
        tv1.addView(nc)
        tabs.add(tv1)

        // Tab Rohdaten
        val tv2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rc = card()
        rawInfo = tv("", 13f, pal.mut)
        rc.addView(rawInfo)
        val rb = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rb.addView(button("Mitschnitt teilen", true).apply { setOnClickListener { share() } }, lp(0, WC, 1f).apply { rightMargin = dp(8) })
        rb.addView(button("Leeren").apply { setOnClickListener { State.clear() } }, lp(0, WC, 1f))
        rc.addView(rb, lp(MP, WC).apply { topMargin = dp(10) })
        rc.addView(button("Alle Zuordnungen löschen").apply {
            background = null
            setTextColor(pal.err)
            setOnClickListener {
                val e = prefs.edit()
                prefs.all.keys.filter { it.startsWith("obis_") || it.startsWith("sc_") }.forEach { e.remove(it) }
                e.apply(); toast("Zuordnungen gelöscht"); render()
            }
        }, lp(WC, WC))
        tv2.addView(rc)
        val dc = card()
        dc.addView(tv("Letztes Telegramm (dekodiert)", 15f, pal.fg, true))
        treeTv = tv("", 12f, pal.fg).also { mono(it) }
        dc.addView(treeTv, lp(MP, WC).apply { topMargin = dp(8) })
        tv2.addView(dc)
        val hc = card()
        hc.addView(tv("Empfangene Bytes (neueste oben)", 15f, pal.fg, true))
        rawTv = tv("", 11f, pal.mut).also { mono(it) }
        hc.addView(rawTv, lp(MP, WC).apply { topMargin = dp(8) })
        tv2.addView(hc)
        tabs.add(tv2)

        tabs.forEach { col.addView(it, lp(MP, WC)) }
        selectTab(tab)
        return root
    }

    private fun selectTab(i: Int) {
        tab = i
        prefs.edit().putInt("tab", i).apply()
        tabs.forEachIndexed { k, v -> v.visibility = if (k == i) View.VISIBLE else View.GONE }
        segBtns.forEachIndexed { k, b ->
            val act = k == i
            b.background = if (act) rounded(if (pal.dark) 0xFF636366.toInt() else 0xFFFFFFFF.toInt(), 8) else null
            b.typeface = if (act) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    // ---------- Verbindung ----------
    private fun toggleConnect() {
        val existing = State.conn
        if (existing != null) {
            existing.stop()
            State.conn = null
            State.setStatus("Getrennt", false)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            render()
            return
        }
        if (State.demo) State.stopDemo()
        val host = hostEt.text.toString().trim()
        val port = portEt.text.toString().split(',', ' ', ';').firstNotNullOfOrNull { it.trim().toIntOrNull() } ?: 4059
        val server = serverCb.isChecked
        if (!server && host.isEmpty()) {
            toast("Bitte IP eingeben oder „Zähler suchen“ tippen")
            return
        }
        prefs.edit().putString("host", host).putString("port", portEt.text.toString())
            .putBoolean("server", server).apply()
        val cm = getSystemService(ConnectivityManager::class.java)
        val net = NetUtil.lanNetwork(cm)
        if (net == null) toast("Kein WLAN erkannt – Verbindung wird trotzdem versucht")
        State.clear()
        val c = Connection(net, host, port, server, { d -> State.onBytes(d) }) { t, ok -> State.setStatus(t, ok) }
        State.conn = c
        c.start()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
    }

    private fun scan() {
        val running = scanner
        if (running != null) {
            running.cancelled = true
            scanner = null
            scanBtn.text = "Zähler suchen"
            scanInfo.text = "Suche abgebrochen"
            return
        }
        val cm = getSystemService(ConnectivityManager::class.java)
        val net = NetUtil.lanNetwork(cm)
        if (net == null) {
            scanInfo.text = "Kein WLAN verbunden. Handy mit dem WLAN des Access Points verbinden."
            return
        }
        val ip = NetUtil.localIpv4(cm, net)
        if (ip == null) {
            scanInfo.text = "Keine IPv4-Adresse im WLAN – vergibt der Access Point Adressen (DHCP)?"
            return
        }
        val ports = portEt.text.toString().split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(4059) }
        val a = ip.first.address.map { it.toInt() and 0xFF }
        val gw = NetUtil.gateway(cm, net)
        val head = "Handy: ${ip.first.hostAddress}/${ip.second}" + (gw?.let { " · Gateway $it" } ?: "")
        scanList.removeAllViews()
        scanInfo.text = "$head\nSuche in ${a[0]}.${a[1]}.${a[2]}.1–254, Port ${ports.joinToString()} …"
        scanBtn.text = "Suche abbrechen"
        var found = 0
        lateinit var s: Scanner
        s = Scanner(net, ip.first, ports,
            onFound = { host, port ->
                runOnUiThread { found++; addScanResult(host, port) }
            },
            onProgress = { done, total ->
                if (done % 40 == 0) runOnUiThread {
                    if (scanner === s) scanInfo.text = "$head\nSuche … ${done * 100 / total} %"
                }
            },
            onDone = {
                runOnUiThread {
                    if (scanner === s) {
                        scanner = null
                        scanBtn.text = "Zähler suchen"
                        scanInfo.text = if (found == 0)
                            "$head\nNichts gefunden auf Port ${ports.joinToString()}. Mehrere Ports mit Komma trennen (z. B. 4059,4061) oder IP aus der Router-Oberfläche eintragen."
                        else "$head\n$found Gerät(e) gefunden – antippen zum Verbinden."
                    }
                }
            })
        scanner = s
        s.start()
    }

    private fun addScanResult(host: String, port: Int) {
        val b = button("$host : $port  →  verbinden").apply {
            setOnClickListener {
                hostEt.setText(host)
                portEt.setText(port.toString())
                serverCb.isChecked = false
                State.conn?.let { it.stop(); State.conn = null }
                toggleConnect()
            }
        }
        scanList.addView(b, lp(MP, WC).apply { topMargin = dp(6) })
    }

    // ---------- Darstellung ----------
    private fun render() {
        if (!::pill.isInitialized) return
        val live = State.connected
        pill.text = when {
            State.demo -> "● Demo"
            live -> "● verbunden"
            State.conn != null -> "◌ verbinde"
            else -> "○ getrennt"
        }
        pill.setTextColor(if (live) pal.onAcc else pal.mut)
        pill.background = rounded(if (live) pal.acc else pal.fill, 99)
        connectBtn.text = if (State.conn != null) "Trennen" else "Verbinden"
        demoBtn.text = if (State.demo) "Demo beenden" else "Demo-Daten anzeigen"
        statusTv.text = State.status
        statusTv.setTextColor(if (live) pal.ok else pal.mut)

        val readings = State.lastApdu?.data?.let {
            try { Interpreter.interpret(it, this) } catch (e: Exception) { emptyList() }
        } ?: emptyList()
        renderValues(readings)
        renderPhasor(readings)
        renderRaw()
    }

    private fun kpiTile(label: String, value: String): View {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(pal.card, 14)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        t.addView(tv(label, 12f, pal.mut))
        t.addView(tv(value, 20f, pal.fg, true))
        return t
    }

    private fun renderValues(rs: List<Reading>) {
        val a = State.lastApdu
        val sb = StringBuilder()
        if (a == null) {
            sb.append("Noch kein Telegramm empfangen. Verbinden oder Demo starten.")
        } else {
            sb.append("Telegramm ").append(State.frames).append(" · ").append(a.kind)
            a.time?.let { sb.append(" · Zählerzeit ").append(it) }
            if (State.interval > 0) sb.append(String.format(Locale.GERMANY, " · Intervall %.1f s", State.interval / 1000.0))
            a.info?.takeIf { a.data == null }?.let { sb.append("\n").append(it) }
        }
        State.lastError?.let { sb.append("\n⚠ ").append(it.lineSequence().first()) }
        infoTv.text = sb.toString()

        // Kennzahlen oben
        val by = HashMap<String, Reading>()
        rs.forEach { r -> r.cde?.let { if (r.value != null && !by.containsKey(it)) by[it] = r } }
        fun v(k: String) = by[k]?.value
        val tiles = ArrayList<Pair<String, String>>()
        val p = v("16.7.0") ?: if (v("1.7.0") != null || v("2.7.0") != null) (v("1.7.0") ?: 0.0) - (v("2.7.0") ?: 0.0) else v("15.7.0")
        if (p != null) tiles.add("Wirkleistung P" to Obis.format(p, "W"))
        val qParts = listOf("5.7.0", "6.7.0", "7.7.0", "8.7.0").map { v(it) }
        val q = if (v("3.7.0") != null || v("4.7.0") != null) (v("3.7.0") ?: 0.0) - (v("4.7.0") ?: 0.0)
        else if (qParts.any { it != null }) (qParts[0] ?: 0.0) + (qParts[1] ?: 0.0) - (qParts[2] ?: 0.0) - (qParts[3] ?: 0.0) else null
        if (q != null) tiles.add("Blindleistung Q" to Obis.format(q, "var"))
        val us = listOf("32.7.0", "52.7.0", "72.7.0").mapNotNull { v(it) }
        if (us.isNotEmpty()) tiles.add("Spannung Ø" to Obis.format(us.average(), "V"))
        val iss = listOf("31.7.0", "51.7.0", "71.7.0").mapNotNull { v(it) }
        if (iss.isNotEmpty()) tiles.add("Strom Ø" to Obis.format(iss.average(), "A"))
        v("14.7.0")?.let { tiles.add("Frequenz" to Obis.format(it, "Hz")) }
        v("13.7.0")?.let { tiles.add("cos φ" to String.format(Locale.GERMANY, "%.3f", it)) }

        kpiBox.removeAllViews()
        var i = 0
        while (i < tiles.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(kpiTile(tiles[i].first, tiles[i].second), lp(0, WC, 1f).apply { rightMargin = dp(6) })
            if (i + 1 < tiles.size) row.addView(kpiTile(tiles[i + 1].first, tiles[i + 1].second), lp(0, WC, 1f).apply { leftMargin = dp(6) })
            else row.addView(View(this), lp(0, 1, 1f))
            kpiBox.addView(row, lp(MP, WC).apply { bottomMargin = dp(12) })
            i += 2
        }

        // Alle Werte nach Gruppen
        valuesBox.removeAllViews()
        if (rs.isEmpty()) return
        val order = (Obis.groups + listOf("Ohne Kennzahl", "Sonstiges")).distinct()
        for (g in order) {
            val list = rs.filter { it.group == g }
            if (list.isEmpty()) continue
            valuesBox.addView(section(g))
            val c = card().apply { setPadding(dp(14), dp(2), dp(14), dp(2)) }
            list.forEachIndexed { k, r ->
                if (k > 0) c.addView(sep())
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(9), 0, dp(9))
                    setOnClickListener { editMapping(r) }
                }
                val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                left.addView(tv(r.name, 15f, pal.fg))
                val sub = when {
                    r.obis != null && r.mapped -> "Position ${r.index} → ${Obis.short(r.obis)} (zugeordnet)"
                    r.obis != null -> "OBIS ${Obis.short(r.obis)}"
                    else -> "Position ${r.index} · antippen zum Zuordnen"
                }
                left.addView(tv(sub, 11.5f, pal.mut))
                row.addView(left, lp(0, WC, 1f))
                row.addView(tv(r.display, 16f, pal.fg, true).apply {
                    gravity = Gravity.END
                    setPadding(dp(8), 0, 0, 0)
                })
                c.addView(row, lp(MP, WC))
            }
            valuesBox.addView(c)
        }
    }

    private fun cell(text: String, color: Int, bold: Boolean = false, weight: Float = 1f): TextView =
        tv(text, 13f, color, bold).apply {
            gravity = Gravity.END
            layoutParams = lp(0, WC, weight)
        }

    private fun renderPhasor(rs: List<Reading>) {
        val vals = HashMap<String, Double>()
        rs.forEach { r -> val c = r.cde; val v = r.value; if (c != null && v != null && !vals.containsKey(c)) vals[c] = v }
        val haveUI = listOf("32.7.0", "52.7.0", "72.7.0", "31.7.0", "51.7.0", "71.7.0").any { vals.containsKey(it) }
        val res = if (haveUI) PhasorCalc.compute(vals, clockwise) else null
        phasorView.result = res

        phasorTable.removeAllViews()
        val hdr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hdr.addView(cell("", pal.mut, weight = 0.5f))
        listOf("U", "I", "φ", "cos φ", "P").forEach { hdr.addView(cell(it, pal.mut, true)) }
        phasorTable.addView(hdr, lp(MP, WC))
        for (k in 0..2) {
            phasorTable.addView(sep())
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
            }
            row.addView(tv("L${k + 1}", 14f, pal.l[k], true).apply { layoutParams = lp(0, WC, 0.5f) })
            val phi = res?.phi?.get(k)
            row.addView(cell(Obis.format(res?.u?.get(k), "V"), pal.fg))
            row.addView(cell(Obis.format(res?.i?.get(k), "A"), pal.fg))
            row.addView(cell(phi?.let { String.format(Locale.GERMANY, "%.1f°", it) } ?: "–", pal.fg))
            row.addView(cell(phi?.let { String.format(Locale.GERMANY, "%.3f", Math.cos(Math.toRadians(it))) } ?: "–", pal.fg))
            row.addView(cell(Obis.format(res?.p?.get(k), "W"), pal.fg))
            phasorTable.addView(row, lp(MP, WC))
        }
        phasorTable.addView(tv("φ positiv = Strom eilt nach (induktiv). P aus U · I · cos φ berechnet.", 11.5f, pal.mut).apply {
            setPadding(0, dp(6), 0, 0)
        })

        notesBox.removeAllViews()
        if (res == null) {
            notesBox.addView(tv(if (State.lastApdu == null) "Noch keine Daten." else
                "Keine Spannungs- oder Stromwerte im Telegramm erkannt (32/52/72.7.0, 31/51/71.7.0). Werte ohne Kennzahl im Tab „Werte“ antippen und zuordnen.", 14f, pal.mut))
        } else {
            res.rotation?.let { notesBox.addView(tv(it, 16f, if (it == "Rechtsdrehfeld") pal.ok else pal.warn, true)) }
            if (!res.hasIAngles) notesBox.addView(tv("Stromwinkel (81.7.4/15/26) fehlen – Ströme können nicht eingezeichnet werden.", 14f, pal.warn))
            if (res.notes.isEmpty() && res.rotation == "Rechtsdrehfeld") notesBox.addView(tv("Keine Auffälligkeiten.", 14f, pal.mut))
            res.notes.forEach { notesBox.addView(tv("• $it", 14f, pal.warn).apply { setPadding(0, dp(4), 0, 0) }) }
        }
        convBtn.text = "Winkelrichtung: " + (if (clockwise) "im Uhrzeigersinn" else "gegen Uhrzeigersinn (mathematisch)") + " – umstellen"
    }

    private fun renderRaw() {
        rawInfo.text = "Telegramme: ${State.frames} · Fehler: ${State.errors} · Bytes: ${State.bytes}" +
            (if (State.lastFrameType.isNotEmpty()) " · Rahmen: ${State.lastFrameType}" else "")
        if (tab != 2) return
        treeTv.text = buildTree()
        rawTv.text = if (State.raw.isEmpty()) "–" else State.raw.take(25).joinToString("\n\n")
    }

    private fun buildTree(): String {
        val sb = StringBuilder()
        val a = State.lastApdu
        if (a == null) sb.append("–") else {
            sb.append(a.kind)
            a.obis?.let { sb.append("  ").append(it) }
            a.info?.let { sb.append("\n").append(it) }
            a.time?.let { sb.append("\nZeit: ").append(it) }
            sb.append("\n\n")
            a.data?.let { sb.append(Dlms.tree(it)) }
        }
        State.lastError?.let { sb.append("\n\nFEHLER: ").append(it) }
        return sb.toString()
    }

    private fun share() {
        val sb = StringBuilder()
        sb.append("IAN-Monitor v").append(BuildConfig.VERSION_NAME).append(" – Mitschnitt\n")
        sb.append("Status: ").append(State.status).append("\n")
        sb.append("Telegramme: ").append(State.frames).append(", Fehler: ").append(State.errors).append("\n\n")
        sb.append("=== Letztes Telegramm ===\n").append(buildTree()).append("\n\n")
        sb.append("=== Rohdaten (neueste oben) ===\n")
        sb.append(State.raw.take(100).joinToString("\n\n"))
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "IAN-Mitschnitt")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(i, "Mitschnitt teilen"))
    }

    // ---------- Zuordnungs-Dialog ----------
    private fun scaleLabel(s: Int): String =
        "× " + BigDecimal.ONE.scaleByPowerOfTen(s).toPlainString().replace('.', ',') + "  (10^$s)"

    private fun editMapping(r: Reading) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        val dlgFg = if (pal.dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        fun label(s: String) = TextView(this).apply {
            text = s; textSize = 13f; setTextColor(pal.mut); setPadding(0, dp(12), 0, dp(4))
        }
        box.addView(TextView(this).apply {
            text = "Rohwert: " + (r.raw?.toString() ?: r.text ?: "–") + "\nAngezeigt: " + r.display
            textSize = 14f; setTextColor(dlgFg)
        })
        val cat = Obis.list
        var obisSp: Spinner? = null
        if (r.obis == null || r.mapped) {
            box.addView(label("Größe zuordnen (Position ${r.index} im Telegramm)"))
            val sp = Spinner(this)
            val items = listOf("– keine Zuordnung –") + cat.map { "${it.name}  (${it.key})" }
            sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
            val cur = obisFor(r.key)
            val idx = cat.indexOfFirst { it.key == cur }
            sp.setSelection(if (idx >= 0) idx + 1 else 0)
            box.addView(sp)
            obisSp = sp
        }
        box.addView(label("Skalierung"))
        val scales = (-6..6).toList()
        val scSp = Spinner(this)
        scSp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("aus Telegramm / automatisch") + scales.map { scaleLabel(it) })
        val cs = scalerFor(r.key)
        scSp.setSelection(if (cs == null) 0 else scales.indexOf(cs) + 1)
        box.addView(scSp)

        val theme = if (pal.dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
        AlertDialog.Builder(this, theme)
            .setTitle(r.name)
            .setView(box)
            .setPositiveButton("Speichern") { _, _ ->
                val e = prefs.edit()
                obisSp?.let { sp ->
                    val i = sp.selectedItemPosition
                    if (i <= 0) e.remove("obis_${r.key}") else e.putString("obis_${r.key}", cat[i - 1].key)
                }
                val si = scSp.selectedItemPosition
                if (si <= 0) e.remove("sc_${r.key}") else e.putInt("sc_${r.key}", scales[si - 1])
                e.apply()
                render()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}
