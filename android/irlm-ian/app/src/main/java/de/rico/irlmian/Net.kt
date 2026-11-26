package de.rico.irlmian

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

object NetUtil {

    /** WLAN- (oder USB-LAN-)Netz suchen. Wichtig, weil Android sonst über mobile Daten routet. */
    @Suppress("DEPRECATION")
    fun lanNetwork(cm: ConnectivityManager): Network? {
        for (n in cm.allNetworks) {
            val c = cm.getNetworkCapabilities(n) ?: continue
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) return n
        }
        return null
    }

    fun localIpv4(cm: ConnectivityManager, n: Network): Pair<Inet4Address, Int>? {
        val lp = cm.getLinkProperties(n) ?: return null
        for (la in lp.linkAddresses) {
            val a = la.address
            if (a is Inet4Address) return a to la.prefixLength
        }
        return null
    }

    fun gateway(cm: ConnectivityManager, n: Network): String? {
        val lp = cm.getLinkProperties(n) ?: return null
        return lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
    }
}

/** Sucht im /24-Netz nach Geräten, die auf den angegebenen Ports lauschen. */
class Scanner(
    private val network: Network?,
    private val base: Inet4Address,
    private val ports: List<Int>,
    private val onFound: (String, Int) -> Unit,
    private val onProgress: (Int, Int) -> Unit,
    private val onDone: () -> Unit
) {
    @Volatile var cancelled = false

    fun start() {
        thread(name = "ian-scan") {
            val pool = Executors.newFixedThreadPool(48)
            val a = base.address.map { it.toInt() and 0xFF }
            val own = a[3]
            val total = 254 * ports.size
            val cnt = AtomicInteger()
            for (h in 1..254) for (port in ports) {
                pool.execute {
                    if (!cancelled && h != own) {
                        val ip = "${a[0]}.${a[1]}.${a[2]}.$h"
                        try {
                            Socket().use { s ->
                                network?.bindSocket(s)
                                s.connect(InetSocketAddress(ip, port), 450)
                                onFound(ip, port)
                            }
                        } catch (_: Exception) {
                        }
                    }
                    onProgress(cnt.incrementAndGet(), total)
                }
            }
            pool.shutdown()
            try { pool.awaitTermination(3, TimeUnit.MINUTES) } catch (_: Exception) {}
            onDone()
        }
    }
}

/** TCP-Verbindung zum Zähler (Client) oder Warten auf den Zähler (Server). Verbindet bei Abbruch neu. */
class Connection(
    private val network: Network?,
    private val host: String,
    private val port: Int,
    private val server: Boolean,
    private val onData: (ByteArray) -> Unit,
    private val onStatus: (String, Boolean) -> Unit
) {
    @Volatile private var running = true
    @Volatile private var sock: Socket? = null
    @Volatile private var srv: ServerSocket? = null

    fun start() {
        thread(name = "ian-conn") {
            while (running) {
                try {
                    val s: Socket = if (server) {
                        onStatus("Warte auf Zähler an Port $port …", false)
                        val ss = ServerSocket()
                        srv = ss
                        ss.reuseAddress = true
                        ss.bind(InetSocketAddress(port))
                        val acc = ss.accept()
                        try { ss.close() } catch (_: Exception) {}
                        acc
                    } else {
                        onStatus("Verbinde mit $host:$port …", false)
                        val so = Socket()
                        sock = so
                        network?.bindSocket(so)
                        so.connect(InetSocketAddress(host, port), 4000)
                        so
                    }
                    sock = s
                    onStatus("Verbunden mit ${s.inetAddress.hostAddress}:${s.port}", true)
                    s.use {
                        val inp = it.getInputStream()
                        val buf = ByteArray(8192)
                        while (running) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            if (n > 0) onData(buf.copyOf(n))
                        }
                    }
                    if (running) onStatus("Verbindung vom Zähler beendet – verbinde neu …", false)
                } catch (e: Exception) {
                    if (running) onStatus("Fehler: ${e.message ?: e.javaClass.simpleName} – neuer Versuch in 3 s", false)
                }
                if (running) try { Thread.sleep(3000) } catch (_: InterruptedException) {}
            }
            onStatus("Getrennt", false)
        }
    }

    fun stop() {
        running = false
        try { sock?.close() } catch (_: Exception) {}
        try { srv?.close() } catch (_: Exception) {}
    }
}
