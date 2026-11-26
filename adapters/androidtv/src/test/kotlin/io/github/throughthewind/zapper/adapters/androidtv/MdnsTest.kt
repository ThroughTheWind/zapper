package io.github.throughthewind.zapper.adapters.androidtv

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MdnsTest {
    @Test
    fun `the query asks for PTR records with a unicast answer`() {
        val query = Mdns.query("_androidtvremote2._tcp.local")
        assertEquals(1, query[5].toInt())
        assertContentEquals(byteArrayOf(0, 12, 0x80.toByte(), 1), query.copyOfRange(query.size - 4, query.size))
    }

    @Test
    fun `a response names the instance, following name compression`() {
        val service = "_androidtvremote2._tcp.local"
        val packet = ByteArrayOutputStream()
        packet.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 2, 0, 0, 0, 0)) // two answers
        // An unrelated A record first.
        name(packet, "tv.local")
        packet.write(byteArrayOf(0, 1, 0, 1, 0, 0, 0, 120, 0, 4, 192.toByte(), 168.toByte(), 1, 20))
        val serviceOffset = packet.size()
        name(packet, service)
        val instance = byteArrayOf(14) + "Living Room TV".toByteArray() + byteArrayOf(0xC0.toByte(), serviceOffset.toByte())
        packet.write(byteArrayOf(0, 12, 0, 1, 0, 0, 0, 120, 0, instance.size.toByte()))
        packet.write(instance)

        assertEquals(listOf("Living Room TV"), Mdns.instanceNames(packet.toByteArray(), service))
        assertEquals(emptyList(), Mdns.instanceNames(packet.toByteArray(), "_googlecast._tcp.local"))
    }

    @Test
    fun `a malformed response yields nothing rather than failing`() {
        assertEquals(emptyList(), Mdns.instanceNames(ByteArray(5), "_androidtvremote2._tcp.local"))
    }

    private fun name(
        out: ByteArrayOutputStream,
        name: String,
    ) {
        name.split('.').forEach {
            out.write(it.length)
            out.write(it.toByteArray())
        }
        out.write(0)
    }
}
