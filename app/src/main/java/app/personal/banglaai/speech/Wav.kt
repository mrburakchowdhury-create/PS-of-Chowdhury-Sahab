package app.personal.banglaai.speech

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal WAV (RIFF, PCM, 16-bit, mono) writer: the AI model takes audio as a complete WAV file. */
object Wav {
    fun encode(pcm: ShortArray, sampleRate: Int = 16000): ByteArray {
        val dataLen = pcm.size * 2
        val b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(dataLen)
        for (s in pcm) b.putShort(s)
        return b.array()
    }
}
