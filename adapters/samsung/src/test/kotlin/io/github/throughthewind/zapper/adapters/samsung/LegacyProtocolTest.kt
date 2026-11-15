package io.github.throughthewind.zapper.adapters.samsung

import java.io.ByteArrayInputStream
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LegacyProtocolTest {
    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    @Test
    fun `a key is framed as type, app and a base64 key name`() {
        val app = "iphone..iapp.samsung".toByteArray()
        val key = "S0VZX1ZPTFVQ".toByteArray() // base64("KEY_VOLUP")
        val payload = bytes(0, 0, 0, key.size, 0) + key

        val expected = bytes(0, app.size, 0) + app + bytes(payload.size, 0) + payload

        assertEquals(expected.toList(), LegacyProtocol.key("KEY_VOLUP").toList())
    }

    @Test
    fun `the handshake carries the phone's address, id and name in base64`() {
        val packet = LegacyProtocol.authenticate("192.168.1.5", "id-1", "Zapper")
        val payload = LegacyProtocol.readPayload(ByteArrayInputStream(packet))

        val expected =
            bytes(0x64, 0) +
                bytes(16, 0) + "MTkyLjE2OC4xLjU=".toByteArray() +
                bytes(8, 0) + "aWQtMQ==".toByteArray() +
                bytes(8, 0) + "WmFwcGVy".toByteArray()
        assertEquals(expected.toList(), payload.toList())
    }

    @Test
    fun `the TV's answers are recognised`() {
        assertEquals(LegacyProtocol.Reply.Granted, LegacyProtocol.reply(FakeLegacyTv.GRANTED))
        assertEquals(LegacyProtocol.Reply.Denied, LegacyProtocol.reply(FakeLegacyTv.DENIED))
        assertEquals(LegacyProtocol.Reply.Waiting, LegacyProtocol.reply(FakeLegacyTv.WAITING))
        assertEquals(LegacyProtocol.Reply.Cancelled, LegacyProtocol.reply(FakeLegacyTv.CANCELLED))
        assertEquals(LegacyProtocol.Reply.KeyAccepted, LegacyProtocol.reply(FakeLegacyTv.KEY_ACCEPTED))
        assertEquals(LegacyProtocol.Reply.Other(bytes(0x42)), LegacyProtocol.reply(bytes(0x42)))
    }

    @Test
    fun `a packet cut short is a closed connection`() {
        assertFailsWith<EOFException> { LegacyProtocol.readPayload(ByteArrayInputStream(bytes(0, 4, 0, 1))) }
    }
}
