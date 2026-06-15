package dev.openandroiduse.companion.agent

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File
import java.security.MessageDigest

/**
 * Owns the on-device model file lifecycle (Phase 5.5): a pinned, integrity-checked
 * download of Gemma 4 E2B (`.litertlm`) to the app's private storage, atomic
 * temp→final rename (mirrors [SessionStore]), and readiness queries the rest of
 * the app gates on. The download runs as a [WorkManager] job so it survives
 * backgrounding; this object holds the pure logic + the enqueue/observe/cancel
 * helpers. Inference itself is Phase 5.5b.
 */
object OnDeviceModelManager {

    // --- Pinned source of trust (content-addressed integrity) ---
    // The repo is Apache-2.0 and ungated. The sha256 is the git-LFS oid of the
    // file, so it pins the exact bytes regardless of branch movement.
    const val REPO = "litert-community/gemma-4-E2B-it-litert-lm"
    const val REVISION = "main"
    const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    const val EXPECTED_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    const val EXPECTED_BYTES = 2_588_147_712L

    /** Free space we want beyond the file itself, so the device isn't left wedged. */
    private const val FREE_SPACE_SLACK_BYTES = 500_000_000L

    const val WORK_NAME = "oau-ondevice-model-download"

    fun downloadUrl(): String = "https://huggingface.co/$REPO/resolve/$REVISION/$FILE_NAME"

    fun modelsDir(context: Context): File = File(context.filesDir, "models")
    fun modelFile(context: Context): File = File(modelsDir(context), FILE_NAME)
    fun partFile(context: Context): File = File(modelsDir(context), "$FILE_NAME.part")

    /** The verified model is present (the .part is renamed to final only after the hash passes). */
    fun isReady(context: Context): Boolean = modelFile(context).let { it.isFile && it.length() > 0 }

    /** Absolute path of the ready model, or null. */
    fun modelPath(context: Context): String? = modelFile(context).takeIf { it.isFile && it.length() > 0 }?.absolutePath

    /** Remove the model (and any partial download) — frees ~2.6 GB. */
    fun delete(context: Context) {
        modelFile(context).delete()
        partFile(context).delete()
    }

    /** Enough room for the model plus working slack. */
    fun hasFreeSpace(context: Context, neededBytes: Long = EXPECTED_BYTES): Boolean {
        val dir = modelsDir(context).apply { mkdirs() }
        return dir.usableSpace > neededBytes + FREE_SPACE_SLACK_BYTES
    }

    /** Lowercase-hex SHA-256 of a file (streamed, constant memory). */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify a downloaded file against the pinned hash. An empty [expected]
     * accepts (only used if a pin is ever unavailable) — production keeps the
     * pin above set, so a corrupt or substituted download is always rejected.
     */
    fun verifyIntegrity(file: File, expected: String = EXPECTED_SHA256): Boolean =
        expected.isEmpty() || sha256(file).equals(expected, ignoreCase = true)

    // --- WorkManager orchestration ---

    fun enqueueDownload(context: Context) {
        val request = OneTimeWorkRequestBuilder<OnDeviceModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelDownload(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Observe the download job as a Flow (progress in [WorkInfo.getProgress]; state for done/failed/running). */
    fun downloadFlow(context: Context): kotlinx.coroutines.flow.Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME)

    const val PROGRESS_PERCENT = "progress_percent"
}
