package dev.openandroiduse.companion.agent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the on-device model (Phase 5.5) as a [WorkManager] job so it survives
 * backgrounding. Streams to a `.part` file, reports percent progress, verifies the
 * pinned SHA-256, then atomically renames to the final file (so a half/corrupt
 * download is never treated as ready). Cancellation deletes the partial file.
 *
 * (A foreground-service download with its own progress notification + the Android
 * 14 `dataSync` service type is a Play-readiness item deferred to Phase 6; a
 * one-time user-initiated WorkManager job is sufficient here.)
 */
class OnDeviceModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        if (OnDeviceModelManager.isReady(context)) return@withContext Result.success()
        if (!OnDeviceModelManager.hasFreeSpace(context)) return@withContext Result.failure()

        val part = OnDeviceModelManager.partFile(context)
        part.parentFile?.mkdirs()
        part.delete()

        try {
            val connection = (URL(OnDeviceModelManager.downloadUrl()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: OnDeviceModelManager.EXPECTED_BYTES
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        var downloaded = 0L
                        var lastPercent = -1
                        while (true) {
                            if (isStopped) {
                                part.delete()
                                return@withContext Result.failure()
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                setProgress(workDataOf(OnDeviceModelManager.PROGRESS_PERCENT to percent))
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (!OnDeviceModelManager.verifyIntegrity(part)) {
                part.delete()
                return@withContext Result.failure()
            }
            val finalFile = OnDeviceModelManager.modelFile(context)
            finalFile.delete()
            if (!part.renameTo(finalFile)) {
                // Cross-filesystem fallback: copy then drop the part.
                part.copyTo(finalFile, overwrite = true)
                part.delete()
            }
            Result.success()
        } catch (_: Exception) {
            part.delete()
            Result.failure()
        }
    }
}
