package de.rico.irlmian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DecoderTest {

    private fun decode(frame: ByteArray, map: Mapping = NoMapping): List<Reading> {
        val frames = FrameAssembler().feed(frame)
        assertEquals(1, frames.size)
        assertEquals("WPDU", frames[0].first)
        val apdu = Dlms.parseApdu(frames[0].second)
        assertEquals("Data-Notification", apdu.kind)
        return Interpreter.interpret(apdu.data!!, map)
    }

    @Test
    fun demoFrameWithObis() {
        val rs = decode(Demo.frame(0))
        val u1 = rs.first { it.cde == "32.7.0" }
        assertEquals(230.4, u1.value!!, 0.05)
        assertEquals("V", u1.unit)
        val i1 = rs.first { it.cde == "31.7.0" }
        assertEquals(42.3, i1.value!!, 0.01)
        val f = rs.first { it.cde == "14.7.0" }
        assertEquals(50.01, f.value!!, 1e-9)
        val e = rs.first { it.cde == "1.8.0" }
        assertEquals(12345670.0, e.value!!, 1e-6)
        // Kennung des Push-Setups darf nicht als Wert auftauchen
        assertTrue(rs.none { it.cde == "25.9.0" })
    }

    @Test
    fun phasorFromDemo() {
        val rs = decode(Demo.frame(0))
        val v = rs.filter { it.cde != null && it.value != null }.associate { it.cde!! to it.value!! }
        val res = PhasorCalc.compute(v, clockwise = false)
        assertEquals("Rechtsdrehfeld", res.rotation)
        assertEquals(18.0, res.phi[0]!!, 0.1)
        assertEquals(22.0, res.phi[1]!!, 0.1)
        assertTrue(res.notes.none { it.contains("prüfen") })
        assertEquals(240.0, res.thU[1]!!, 0.01)
    }

    @Test
    fun splitAcrossTcpReads() {
        val fr = Demo.frame(3)
        val asm = FrameAssembler()
        assertEquals(0, asm.feed(fr.copyOfRange(0, 5)).size)
        assertEquals(0, asm.feed(fr.copyOfRange(5, 40)).size)
        val out = asm.feed(fr.copyOfRange(40, fr.size) + Demo.frame(4))
        assertEquals(2, out.size)
    }

    @Test
    fun valuesOnlyWithMapping() {
        val map = object : Mapping {
            override fun obisFor(key: String) = if (key == "#1") "32.7.0" else null
            override fun scalerFor(key: String) = if (key == "#1") -1 else null
        }
        val rs = decode(Demo.frameValuesOnly(), map)
        assertEquals(4, rs.size)
        val u1 = rs[0]
        assertNotNull(u1.obis)
        assertEquals("Spannung L1", u1.name)
        assertEquals(230.4, u1.value!!, 1e-9)
        assertEquals("Wert 2", rs[1].name)
    }

    @Test
    fun hdlcFrame() {
        // HDLC-Rahmen mit LLC-Header um eine Data-Notification
        val apdu = byteArrayOf(0x0F, 0, 0, 0, 1, 0, 2, 1, 18, 0x09, 0x01)
        val info = byteArrayOf(0xE6.toByte(), 0xE7.toByte(), 0) + apdu
        val inner = byteArrayOf(0x03, 0x21, 0x13, 0, 0) + info + byteArrayOf(0, 0) // Adressen, Control, HCS … FCS
        val len = inner.size + 2
        val frame = byteArrayOf(0x7E, (0xA0 or (len shr 8)).toByte(), len.toByte()) + inner + byteArrayOf(0x7E)
        val out = FrameAssembler().feed(frame)
        assertEquals(1, out.size)
        val a = Dlms.parseApdu(out[0].second)
        val rs = Interpreter.interpret(a.data!!)
        assertTrue(abs(rs[0].raw!! - 2305.0) < 1e-9)
    }
}
