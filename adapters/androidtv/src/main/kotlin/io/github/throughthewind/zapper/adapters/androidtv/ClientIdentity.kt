package io.github.throughthewind.zapper.adapters.androidtv

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The phone's credential for one TV: an RSA key and a self-signed certificate for it. The TV
 * remembers the certificate when pairing succeeds and lets it in on the remote port from then on,
 * so it is stored as the session's token (research §3).
 */
internal class ClientIdentity(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
) {
    val publicKey: RSAPublicKey get() = certificate.publicKey as RSAPublicKey

    /** `<PKCS#8 key>.<DER certificate>`, both Base64, for the credential store. */
    fun encode(): String {
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(privateKey.encoded) + "." + encoder.encodeToString(certificate.encoded)
    }

    companion object {
        private const val KEY_BITS = 2048
        private const val VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000

        /** A new key and certificate named [commonName]. Takes up to a second on a phone. */
        fun generate(commonName: String): ClientIdentity {
            val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val der = Der.selfSignedCertificate(commonName, keys.public.encoded, keys.private, Date(now), Date(now + VALIDITY_MS))
            return ClientIdentity(keys.private, certificate(der))
        }

        /** The identity [encode] wrote, or null when [value] is not one. */
        fun decode(value: String): ClientIdentity? =
            runCatching {
                val (key, cert) = value.split('.').also { require(it.size == 2) }
                val decoder = Base64.getDecoder()
                ClientIdentity(
                    KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(decoder.decode(key))),
                    certificate(decoder.decode(cert)),
                )
            }.getOrNull()

        fun certificate(der: ByteArray): X509Certificate =
            CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
    }
}

/**
 * Just enough DER to write a self-signed X.509 certificate and to read a certificate's subject.
 * The JDK can parse certificates but has no public API to make one, and Bouncy Castle would be a
 * large dependency for one structure.
 */
internal object Der {
    private const val INTEGER = 0x02
    private const val BIT_STRING = 0x03
    private const val OCTET_STRING = 0x04
    private const val NULL = 0x05
    private const val OID = 0x06
    private const val UTF8_STRING = 0x0C
    private const val SEQUENCE = 0x30
    private const val SET = 0x31
    private const val UTC_TIME = 0x17
    private const val GENERALIZED_TIME = 0x18
    private const val BOOLEAN = 0x01

    private const val SHA256_WITH_RSA = "1.2.840.113549.1.1.11"
    private const val COMMON_NAME = "2.5.4.3"
    private const val DN_QUALIFIER = "2.5.4.46"
    private const val BASIC_CONSTRAINTS = "2.5.29.19"

    /**
     * A version 3 certificate for [publicKeyInfo] (an encoded `SubjectPublicKeyInfo`), issued by
     * and to [commonName], signed with [signingKey], with a CA basic constraint as the reference
     * implementation's has. Its DNS alternative name is left out: a name with spaces or slashes is
     * not a valid one, and the TV does not look at it.
     */
    fun selfSignedCertificate(
        commonName: String,
        publicKeyInfo: ByteArray,
        signingKey: PrivateKey,
        notBefore: Date,
        notAfter: Date,
    ): ByteArray {
        val algorithm = sequence(oid(SHA256_WITH_RSA), tlv(NULL, ByteArray(0)))
        val name = sequence(set(sequence(oid(COMMON_NAME), tlv(UTF8_STRING, commonName.toByteArray(Charsets.UTF_8)))))
        val extensions =
            sequence(
                sequence(oid(BASIC_CONSTRAINTS), tlv(OCTET_STRING, sequence(tlv(BOOLEAN, byteArrayOf(-1)), integer(BigInteger.ZERO)))),
            )
        val tbs =
            sequence(
                tlv(0xA0, integer(BigInteger.valueOf(2))),
                integer(BigInteger.valueOf(System.currentTimeMillis())),
                algorithm,
                name,
                sequence(time(notBefore), time(notAfter)),
                name,
                publicKeyInfo,
                tlv(0xA3, extensions),
            )
        val signature =
            Signature.getInstance("SHA256withRSA").run {
                initSign(signingKey)
                update(tbs)
                sign()
            }
        return sequence(tbs, algorithm, tlv(BIT_STRING, byteArrayOf(0) + signature))
    }

    /** The subject's common name and DN qualifier, from an encoded X.500 name. */
    fun subjectNames(encodedName: ByteArray): Map<String, String> {
        val names = mutableMapOf<String, String>()
        val name = readElement(encodedName, 0).first
        if (name.tag != SEQUENCE) throw IOException("Not a name")
        for (set in children(name.content)) {
            for (attribute in children(set.content)) {
                val parts = children(attribute.content)
                if (parts.size < 2 || parts[0].tag != OID) continue
                val type =
                    when (decodeOid(parts[0].content)) {
                        COMMON_NAME -> "CN"
                        DN_QUALIFIER -> "dnQualifier"
                        else -> continue
                    }
                names[type] = parts[1].content.toString(Charsets.UTF_8)
            }
        }
        return names
    }

    private class Element(
        val tag: Int,
        val content: ByteArray,
    )

    /** The element at [start] and the offset after it. */
    private fun readElement(
        data: ByteArray,
        start: Int,
    ): Pair<Element, Int> {
        var pos = start
        if (pos + 2 > data.size) throw IOException("Truncated DER")
        val tag = data[pos++].toInt() and 0xFF
        var length = data[pos++].toInt() and 0xFF
        if (length and 0x80 != 0) {
            val count = length and 0x7F
            if (count !in 1..3 || pos + count > data.size) throw IOException("Bad DER length")
            length = 0
            repeat(count) { length = (length shl 8) or (data[pos++].toInt() and 0xFF) }
        }
        if (pos + length > data.size) throw IOException("Truncated DER")
        return Element(tag, data.copyOfRange(pos, pos + length)) to pos + length
    }

    private fun children(data: ByteArray): List<Element> {
        val out = mutableListOf<Element>()
        var pos = 0
        while (pos < data.size) {
            val (element, next) = readElement(data, pos)
            out += element
            pos = next
        }
        return out
    }

    private fun decodeOid(content: ByteArray): String {
        if (content.isEmpty()) return ""
        val parts = mutableListOf<Long>()
        val first = content[0].toInt() and 0xFF
        parts += (first / 40).toLong()
        parts += (first % 40).toLong()
        var value = 0L
        for (i in 1 until content.size) {
            val b = content[i].toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F).toLong()
            if (b and 0x80 == 0) {
                parts += value
                value = 0
            }
        }
        return parts.joinToString(".")
    }

    private fun tlv(
        tag: Int,
        content: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val size = content.size
        when {
            size < 0x80 -> {
                out.write(size)
            }

            size < 0x100 -> {
                out.write(0x81)
                out.write(size)
            }

            size < 0x10000 -> {
                out.write(0x82)
                out.write(size shr 8)
                out.write(size and 0xFF)
            }

            else -> {
                out.write(0x83)
                out.write(size shr 16)
                out.write((size shr 8) and 0xFF)
                out.write(size and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun sequence(vararg parts: ByteArray) = tlv(SEQUENCE, parts.fold(ByteArray(0)) { acc, b -> acc + b })

    private fun set(vararg parts: ByteArray) = tlv(SET, parts.fold(ByteArray(0)) { acc, b -> acc + b })

    private fun integer(value: BigInteger) = tlv(INTEGER, value.toByteArray())

    private fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * 40 + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            val groups = mutableListOf((arc and 0x7F).toInt())
            var rest = arc ushr 7
            while (rest > 0) {
                groups += ((rest and 0x7F) or 0x80).toInt()
                rest = rest ushr 7
            }
            groups.reversed().forEach { out.write(it) }
        }
        return tlv(OID, out.toByteArray())
    }

    private fun time(date: Date): ByteArray {
        val utc = TimeZone.getTimeZone("UTC")
        val year =
            java.util.Calendar
                .getInstance(utc)
                .apply { time = date }
                .get(java.util.Calendar.YEAR)
        // RFC 5280 §4.1.2.5: UTCTime through 2049, GeneralizedTime from 2050.
        val (pattern, tag) = if (year < 2050) "yyMMddHHmmss'Z'" to UTC_TIME else "yyyyMMddHHmmss'Z'" to GENERALIZED_TIME
        val text = SimpleDateFormat(pattern, Locale.US).apply { timeZone = utc }.format(date)
        return tlv(tag, text.toByteArray(Charsets.US_ASCII))
    }
}
