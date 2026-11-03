package dev.or2.central.worldlink.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import java.nio.charset.StandardCharsets

object PacketRegistry {
    private val inbound = linkedMapOf<Int, String>()
    private val outbound = linkedMapOf<Int, String>()

    fun registerInbound(opcode: Int, name: String) {
        inbound[opcode] = name
    }

    fun registerOutbound(opcode: Int, name: String) {
        outbound[opcode] = name
    }

    fun inboundName(opcode: Int): String? = inbound[opcode]

    fun isKnownInbound(opcode: Int): Boolean = opcode in inbound
}

class FrameReader(
    val opcode: Int,
    private val buf: ByteBuf,
    val remainingAfterOpcode: Int,
) : AutoCloseable {
    fun trailingUnreadBytes(): Int = buf.readableBytes()

    fun readMagic(): Int = buf.readInt()

    fun readUnsignedShort(): Int = buf.readUnsignedShort().toInt()

    fun readUnsignedByte(): Int = buf.readUnsignedByte().toInt()

    fun readInt(): Int = buf.readInt()

    fun readLong(): Long = buf.readLong()

    fun readFully(len: Int): ByteArray {
        val out = ByteArray(len)
        buf.readBytes(out)
        return out
    }

    fun readUtf8LenPrefixed(): String {
        val len = buf.readUnsignedShort()
        val bytes = readFully(len)
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun readLenPrefixedBytes(): ByteArray {
        val len = buf.readUnsignedShort()
        return readFully(len)
    }

    fun readFixedToken(expectedBytes: Int = WorldOpcodes.TOKEN_BYTES): ByteArray {
        val len = buf.readUnsignedShort()
        if (len != expectedBytes) {
            throw PacketDecodeException("expected token length $expectedBytes but got $len")
        }
        val token = readFully(len)
        if (trailingUnreadBytes() != 0) {
            throw PacketDecodeException("unexpected trailing bytes after token")
        }
        return token
    }

    /** Session token prefix for social frames that include fields after the token. */
    fun readSessionToken(expectedBytes: Int = WorldOpcodes.TOKEN_BYTES): ByteArray {
        val len = buf.readUnsignedShort()
        if (len != expectedBytes) {
            throw PacketDecodeException("expected token length $expectedBytes but got $len")
        }
        return readFully(len)
    }

    fun requireFullyConsumed() {
        if (trailingUnreadBytes() != 0) {
            throw PacketDecodeException("unexpected trailing bytes")
        }
    }

    override fun close() {
        buf.release()
    }
}

fun readInboundFrame(payload: ByteArray): FrameReader =
    readInboundFrame(Unpooled.wrappedBuffer(payload))

fun readInboundFrame(buf: ByteBuf): FrameReader {
    val opcode = buf.readUnsignedByte().toInt()
    return FrameReader(opcode, buf, buf.readableBytes())
}

class FrameWriter(
    private val opcode: Int,
    private val buf: ByteBuf = Unpooled.buffer(),
) {
    init {
        buf.writeByte(opcode)
    }

    fun writeByte(v: Int) {
        buf.writeByte(v)
    }

    fun writeShort(v: Int) {
        buf.writeShort(v)
    }

    fun writeInt(v: Int) {
        buf.writeInt(v)
    }

    fun writeLong(v: Long) {
        buf.writeLong(v)
    }

    fun writeBytes(bytes: ByteArray) {
        buf.writeBytes(bytes)
    }

    fun writeUtf8LenPrefixed(value: String) {
        val utf8 = value.toByteArray(StandardCharsets.UTF_8)
        require(utf8.size <= 65535) { "utf8 length" }
        buf.writeShort(utf8.size)
        buf.writeBytes(utf8)
    }

    fun writeUtf8Truncated(value: String, maxBytes: Int) {
        val utf8 = utf8TruncatedTo(value, maxBytes)
        buf.writeShort(utf8.size)
        buf.writeBytes(utf8)
    }

    fun build(): ByteArray =
        try {
            ByteBufUtil.getBytes(buf)
        } finally {
            buf.release()
        }
}

fun outboundFrame(opcode: Int, block: FrameWriter.() -> Unit): ByteArray =
    FrameWriter(opcode).apply(block).build()

fun utf8TruncatedTo(s: String, maxBytes: Int): ByteArray {
    val full = s.toByteArray(StandardCharsets.UTF_8)
    if (full.size <= maxBytes) return full
    var cut = maxBytes
    while (cut > 0 && (full[cut - 1].toInt() and 0xC0) == 0x80) {
        cut--
    }
    return full.copyOf(cut)
}
