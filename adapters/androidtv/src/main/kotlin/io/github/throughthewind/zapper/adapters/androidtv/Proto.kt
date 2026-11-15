package io.github.throughthewind.zapper.adapters.androidtv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * The few protobuf wire-format pieces the Android TV Remote protocol uses: varints, strings, bytes
 * and nested messages. Written by hand rather than generated, since the two `.proto` files the
 * protocol needs use about twenty fields between them (research §3).
 */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(
        field: Int,
        value: Long,
    ) = apply {
        tag(field, VARINT)
        rawVarint(value)
    }

    fun int(
        field: Int,
        value: Int,
    ) = int(field, value.toLong())

    fun bytes(
        field: Int,
        value: ByteArray,
    ) = apply {
        tag(field, LENGTH_DELIMITED)
        rawVarint(value.size.toLong())
        out.write(value)
    }

    fun string(
        field: Int,
        value: String,
    ) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(
        field: Int,
        build: ProtoWriter.() -> Unit,
    ) = bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(
        field: Int,
        wireType: Int,
    ) = rawVarint((field.toLong() shl 3) or wireType.toLong())

    private fun rawVarint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    companion object {
        const val VARINT = 0
        const val FIXED64 = 1
        const val LENGTH_DELIMITED = 2
        const val FIXED32 = 5
    }
}

/** A decoded message: each field number with the values it carried, last one winning on reads. */
internal class ProtoMessage private constructor(
    private val fields: Map<Int, List<Any>>,
) {
    fun has(field: Int): Boolean = field in fields

    fun int(field: Int): Long = fields[field]?.lastOrNull() as? Long ?: 0L

    fun bytes(field: Int): ByteArray = fields[field]?.lastOrNull() as? ByteArray ?: ByteArray(0)

    fun string(field: Int): String = bytes(field).toString(Charsets.UTF_8)

    fun message(field: Int): ProtoMessage? = (fields[field]?.lastOrNull() as? ByteArray)?.let { parse(it) }

    companion object {
        /** Parses [data]; throws [IOException] when it is not well-formed protobuf. */
        fun parse(data: ByteArray): ProtoMessage {
            val fields = mutableMapOf<Int, MutableList<Any>>()
            var pos = 0

            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (pos >= data.size || shift > 63) throw IOException("Truncated varint")
                    val b = data[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }

            fun take(n: Int): ByteArray {
                if (n < 0 || pos + n > data.size) throw IOException("Truncated field")
                return data.copyOfRange(pos, pos + n).also { pos += n }
            }

            while (pos < data.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                val value: Any =
                    when ((key and 7).toInt()) {
                        ProtoWriter.VARINT -> varint()
                        ProtoWriter.LENGTH_DELIMITED -> take(varint().toInt())
                        ProtoWriter.FIXED64 -> take(8)
                        ProtoWriter.FIXED32 -> take(4)
                        else -> throw IOException("Unsupported wire type in field $field")
                    }
                fields.getOrPut(field) { mutableListOf() }.add(value)
            }
            return ProtoMessage(fields)
        }
    }
}

/** Both ports frame each message with its length as a varint (research §3). */
internal object ProtoFrames {
    /** Largest message accepted; the TV's are a few hundred bytes. */
    private const val MAX_SIZE = 64 * 1024

    fun frame(message: ByteArray): ByteArray {
        val prefix = ByteArrayOutputStream()
        var v = message.size
        while (v and 0x7F.inv() != 0) {
            prefix.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        prefix.write(v)
        return prefix.toByteArray() + message
    }

    /** Reads one message. Throws [EOFException] when the TV closed the connection. */
    fun read(input: InputStream): ProtoMessage {
        var size = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("The TV closed the connection")
            size = size or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 28) throw IOException("Bad message length")
        }
        if (size > MAX_SIZE) throw IOException("Message of $size bytes is too large")
        val data = ByteArray(size)
        var read = 0
        while (read < size) {
            val n = input.read(data, read, size - read)
            if (n < 0) throw EOFException("The TV closed the connection")
            read += n
        }
        return ProtoMessage.parse(data)
    }
}
