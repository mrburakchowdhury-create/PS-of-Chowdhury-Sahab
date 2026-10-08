package app.personal.banglaai.security

import com.google.crypto.tink.StreamingAead
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SeekableByteChannel
import java.nio.channels.WritableByteChannel

/**
 * Wraps the real [StreamingAead] of ONE unlock session. Every stream/channel it creates re-checks, on every read/write, that the session is still the
 * current one; after [KeyVault.lock] (or a later unlock, which starts a NEW session) all of them fail with [VaultLockedException]. So a job, callback or
 * half-read file that outlives a lock cannot keep decrypting or encrypting with the old session. (The JVM may still hold copies of key material inside Tink
 * objects until they are garbage-collected; that cannot be forced, so this is "cut off use", not a guarantee of erasure.)
 */
internal class GuardedStreamingAead(private val inner: StreamingAead, private val alive: () -> Boolean) : StreamingAead {
    private fun check() { if (!alive()) throw VaultLockedException() }

    override fun newEncryptingStream(ciphertextDestination: OutputStream, associatedData: ByteArray): OutputStream {
        check()
        return object : FilterOutputStream(inner.newEncryptingStream(ciphertextDestination, associatedData)) {
            override fun write(b: Int) { check(); out.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { check(); out.write(b, off, len) }
            override fun flush() { check(); out.flush() }
            override fun close() { if (!alive()) { runCatching { ciphertextDestination.close() }; throw VaultLockedException() }; out.close() }
        }
    }

    override fun newDecryptingStream(ciphertextSource: InputStream, associatedData: ByteArray): InputStream {
        check()
        return object : FilterInputStream(inner.newDecryptingStream(ciphertextSource, associatedData)) {
            override fun read(): Int { check(); return `in`.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int { check(); return `in`.read(b, off, len) }
            override fun skip(n: Long): Long { check(); return `in`.skip(n) }
            override fun available(): Int { check(); return `in`.available() }
        }
    }

    override fun newEncryptingChannel(ciphertextDestination: WritableByteChannel, associatedData: ByteArray): WritableByteChannel {
        check(); val c = inner.newEncryptingChannel(ciphertextDestination, associatedData)
        return object : WritableByteChannel { override fun write(src: ByteBuffer): Int { check(); return c.write(src) }; override fun isOpen() = c.isOpen; override fun close() = c.close() }
    }

    override fun newDecryptingChannel(ciphertextChannel: ReadableByteChannel, associatedData: ByteArray): ReadableByteChannel {
        check(); val c = inner.newDecryptingChannel(ciphertextChannel, associatedData)
        return object : ReadableByteChannel { override fun read(dst: ByteBuffer): Int { check(); return c.read(dst) }; override fun isOpen() = c.isOpen; override fun close() = c.close() }
    }

    override fun newSeekableDecryptingChannel(ciphertextChannel: SeekableByteChannel, associatedData: ByteArray): SeekableByteChannel {
        check(); val c = inner.newSeekableDecryptingChannel(ciphertextChannel, associatedData)
        return object : SeekableByteChannel {
            override fun read(dst: ByteBuffer): Int { check(); return c.read(dst) }
            override fun write(src: ByteBuffer): Int { check(); return c.write(src) }
            override fun position(): Long = c.position()
            override fun position(newPosition: Long): SeekableByteChannel { check(); c.position(newPosition); return this }
            override fun size(): Long = c.size()
            override fun truncate(size: Long): SeekableByteChannel { check(); c.truncate(size); return this }
            override fun isOpen() = c.isOpen
            override fun close() = c.close()
        }
    }
}
