package app.xani.feature.upload

import android.content.Context
import app.xani.core.isolate.runFeature
import org.json.JSONObject
import java.io.File

/**
 * One JSON file per task under `files/uploads/`. Writes are atomic (temp +
 * rename) so a crash mid-write cannot corrupt the queue, and a terminal task
 * is removed once the user has seen its strip.
 */
internal class UploadStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "uploads").apply { mkdirs() }

    fun loadAll(): List<UploadTask> = runFeature(emptyList()) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { f ->
                runFeature<UploadTask?>(null) { UploadTask.from(JSONObject(f.readText())) }
                    ?: run { f.delete(); null }
            }
            ?.sortedBy { it.createdAt }
            .orEmpty()
    }

    fun save(task: UploadTask) = runFeature(Unit) {
        val target = File(dir, "${task.id}.json")
        val tmp = File(dir, "${task.id}.json.tmp")
        tmp.writeText(task.toJson().toString())
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun delete(id: String) = runFeature(Unit) {
        File(dir, "$id.json").delete()
        File(dir, "$id.json.tmp").delete()
    }
}
