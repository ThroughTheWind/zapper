package io.github.throughthewind.zapper.core.remote

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WakeOnLanTest {
    @Test
    fun `the magic packet is six 0xFF then the MAC sixteen times`() {
        val packet = WakeOnLan.packet("AA:bb:cc:dd:ee:01")

        assertEquals(102, packet.size)
        assertContentEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        val mac = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0x01)
        (0 until 16).forEach { assertContentEquals(mac, packet.copyOfRange(6 + it * 6, 12 + it * 6)) }
    }

    @Test
    fun `dashes work too, anything else is not a MAC`() {
        assertContentEquals(WakeOnLan.packet("aa:bb:cc:dd:ee:01"), WakeOnLan.packet("aa-bb-cc-dd-ee-01"))
        assertFailsWith<IllegalArgumentException> { WakeOnLan.packet("aa:bb:cc") }
        assertFailsWith<NumberFormatException> { WakeOnLan.packet("zz:bb:cc:dd:ee:01") }
    }

    @Test
    fun `the packet goes to the limited broadcast and the TV's subnet`() {
        assertEquals(listOf("255.255.255.255", "192.168.1.255"), WakeOnLan.broadcastAddresses("192.168.1.20"))
        assertEquals(listOf("255.255.255.255"), WakeOnLan.broadcastAddresses("tv.local"))
    }
}
