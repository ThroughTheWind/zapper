package io.github.throughthewind.zapper.adapters.androidtv

import io.github.throughthewind.zapper.core.model.RemoteKey
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidTvProtocolTest {
    @Test
    fun `the pairing secret matches the reference implementation`() {
        // Computed with tronikos/androidtvremote2's algorithm (Python) for two 1024-bit keys.
        val secret = Polo.secretFor(rsa(CLIENT_MODULUS), rsa(SERVER_MODULUS), "253F9C")
        assertEquals("25b39457b55d03391cfb62668ae73a9abc37d5828389e4698786ccb1ea43cf2c", assertNotNull(secret).toHex())
    }

    @Test
    fun `a code whose check byte does not match is caught locally`() {
        assertNull(Polo.secretFor(rsa(CLIENT_MODULUS), rsa(SERVER_MODULUS), "263f9c"))
        assertNull(Polo.secretFor(rsa(CLIENT_MODULUS), rsa(SERVER_MODULUS), "25 3f9"))
        assertNull(Polo.secretFor(rsa(CLIENT_MODULUS), rsa(SERVER_MODULUS), "25zz9c"))
    }

    @Test
    fun `protobuf round-trips nested messages and multi-byte varints`() {
        val bytes =
            ProtoWriter()
                .int(1, 300)
                .string(2, "héllo")
                .message(90) { string(1, "https://www.youtube.com") }
                .int(3, -1)
                .toByteArray()
        val message = ProtoMessage.parse(bytes)
        assertEquals(300, message.int(1))
        assertEquals("héllo", message.string(2))
        assertEquals("https://www.youtube.com", message.message(90)?.string(1))
        assertEquals(-1, message.int(3))
        assertEquals(0, message.int(4))
        assertTrue(!message.has(4))
    }

    @Test
    fun `messages are framed with a varint length`() {
        val payload = ByteArray(200) { 1 }
        val framed = ProtoFrames.frame(payload)
        assertContentEquals(byteArrayOf(0xC8.toByte(), 0x01), framed.copyOfRange(0, 2))
        val key = Remote.key(24, Remote.SHORT)
        val read = ProtoFrames.read(ProtoFrames.frame(key).inputStream())
        assertEquals(24, read.message(Remote.KEY_INJECT)?.int(1))
        assertEquals(3, read.message(Remote.KEY_INJECT)?.int(2))
    }

    @Test
    fun `a key press encodes as the reference's RemoteKeyInject`() {
        // remote_key_inject { key_code: KEYCODE_DPAD_UP, direction: SHORT }, in protobuf wire format.
        assertEquals("520408131003", Remote.key(19, Remote.SHORT).toHex())
    }

    @Test
    fun `the pairing request carries the client name`() {
        val message = ProtoMessage.parse(Polo.pairingRequest("Zapper"))
        assertEquals(2, message.int(1))
        assertEquals(200, message.int(2))
        assertEquals("atvremote", message.message(Polo.PAIRING_REQUEST)?.string(1))
        assertEquals("Zapper", message.message(Polo.PAIRING_REQUEST)?.string(2))
    }

    @Test
    fun `a generated identity survives the credential store`() {
        val identity = ClientIdentity.generate("Zapper")
        val decoded = assertNotNull(ClientIdentity.decode(identity.encode()))
        assertContentEquals(identity.certificate.encoded, decoded.certificate.encoded)
        assertContentEquals(identity.privateKey.encoded, decoded.privateKey.encoded)
        assertEquals("CN=Zapper", identity.certificate.subjectX500Principal.name)
        identity.certificate.verify(identity.publicKey)
        assertNull(ClientIdentity.decode("not an identity"))
    }

    @Test
    fun `a certificate without a model still gives the MAC`() {
        val nexus = FakeAndroidTv.serverIdentity("atvremote/11:22:33:44:55:66").certificate
        val tv = AndroidTvAdapter.describe("10.0.0.5", nexus)
        assertEquals("11:22:33:44:55:66", tv.macAddress)
        assertEquals("Android TV", tv.name)
    }

    @Test
    fun `every known app has a link`() {
        io.github.throughthewind.zapper.core.model.KnownApps.all
            .forEach { assertTrue(it.id in ANDROID_TV_APP_LINKS, it.id) }
        assertEquals("plex://", androidTvAppLink("plex"))
        assertEquals("vnd.youtube://abc", androidTvAppLink("vnd.youtube://abc"))
    }

    @Test
    fun `the standard keys have codes`() {
        assertEquals(26, RemoteKey.POWER.androidTvCode())
        assertEquals(7, RemoteKey.DIGIT_0.androidTvCode())
        assertEquals(16, RemoteKey.DIGIT_9.androidTvCode())
        assertNull(RemoteKey.CHANNEL_LIST.androidTvCode())
    }

    private fun rsa(modulus: String): RSAPublicKey =
        KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(modulus, 16), BigInteger.valueOf(65537))) as RSAPublicKey

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        const val CLIENT_MODULUS =
            "ac41fb80417a0dc6e553652607902d19ee9df43f42a12e608f9cbddbc939a338078fabca207843db97c058cdf8485425281b9ce6f455b0e675918f" +
                "f4ecbe17456371604d6aaed16e10f4c07240d2bab0a61e0cf774056cc56dfa7de18ea2449bf859b015f24cfe80d5a6df9e8ef34d7cd248ba062a" +
                "755f3761e71b55036a84ff"
        const val SERVER_MODULUS =
            "c03379ee690c8c3d691a9d7e8e6eb86971ec97242404a0795ed8333cec0dab58329195614130ec930864670d070bec93cd8c4732f0104b8c17f2" +
                "cd3c5ffcd5dd3c29c81c15028c75722469eac87e49daddf50d49e02e7cf0ba9d5476de7fe8cd636efb0ff498e59129991e475f54d1de317496c4" +
                "c32d6641e5649f87df4d9ae9"
    }
}
