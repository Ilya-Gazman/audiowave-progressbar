package rm.com.audiowave

import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Created by alex
 */
internal val MAIN_THREAD = Handler(Looper.getMainLooper())
internal val SAMPLER_THREAD: ExecutorService = Executors.newSingleThreadExecutor()

object Sampler {

    fun downSampleAsync(file: File, targetSize: Int, answer: (ByteArray) -> Unit) {
        SAMPLER_THREAD.submit {
            val scaled = downSample(file, targetSize)

            MAIN_THREAD.post {
                answer(scaled)
            }
        }
    }

    fun downSample(file: File, targetSize: Int): ByteArray {
        val targetSized = ByteArray(targetSize)
        val length = file.length()
        val chunkSize = length / targetSize
        val chunkStep = Math.max(Math.floor((chunkSize / 10.0)), 1.0).toLong()

        var prevDataIndex = 0
        var sampledPerChunk = 0F
        var sumPerChunk = 0F

        if (targetSize >= length) {
            return targetSized.paste(file)
        }


        FileInputStream(file).use { inputStream ->
            val buffer: ByteArray = kotlin.ByteArray(chunkStep.toInt())
            for (index in 0 until length step chunkStep) {
                val size = inputStream.read(buffer);
                val total = (0 until size).sumByDouble { Math.abs(buffer[it].toInt()).toDouble() };
                val data: Int = (total / size).toInt()
                val currentDataIndex = (targetSize * index / length).toInt()

                if (prevDataIndex == currentDataIndex) {
                    sampledPerChunk += 1
                    sumPerChunk += data
                } else {
                    targetSized[prevDataIndex] = (sumPerChunk / sampledPerChunk).toByte()

                    sumPerChunk = 0F
                    sampledPerChunk = 0F
                    prevDataIndex = currentDataIndex
                }
            }
        }
        return targetSized
    }
}

internal val Byte.abs: Byte
    get() = when (this) {
        Byte.MIN_VALUE -> Byte.MAX_VALUE
        in (Byte.MIN_VALUE + 1..0) -> (-this).toByte()
        else -> this
    }

internal fun ByteArray.paste(other: ByteArray): ByteArray {
    if (size == 0) return byteArrayOf()

    return this.apply {
        forEachIndexed { i, _ ->
            this[i] = other.getOrElse(i, { this[i].abs })
        }
    }
}

internal fun ByteArray.paste(file: File): ByteArray {
    if (size == 0) return byteArrayOf()

    return this.apply {
        file.forEachBlock { buffer, bytesRead ->
            for (i in 0..bytesRead) this[i] = buffer[i]
        }
    }
}
