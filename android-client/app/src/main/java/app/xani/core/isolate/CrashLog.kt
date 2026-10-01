package app.xani.core.isolate

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort crash record.
 *
 * [runFeature] and [FeatureGate] contain failures we can see coming; a throw
 * inside a composition or a coroutine we do not own still takes the process
 * down, and on a user's device there is no logcat to read afterwards. This
 * writes the reason of the last death to one small file so the next launch can
 * report it — the difference between "it crashes on post" and a stack trace.
 *
 * Never writes a message body, token, or any field the user typed: only the
 * exception types, their messages, and app frames.
 */
object CrashLog {

    private const val TAG = "XaniCrash"
    private const val FILE = "crash-last.txt"
    private const val MAX_FRAMES = 24

    @Volatile
    private var dir: File? = null

    /** Call once, as early as possible. Chains to the handler already installed. */
    fun install(context: Context) {
        if (dir != null) return
        dir = context.applicationContext.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runFeature(Unit) { write(thread.name, error) }
            Log.e(TAG, "fatal on ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    /** The previous death, or null. Clears the record so it is reported once. */
    fun consume(): String? = runFeature(null) {
        val f = File(dir ?: return@runFeature null, FILE)
        if (!f.isFile) return@runFeature null
        val text = f.readText().ifBlank { null }
        f.delete()
        text
    }

    private fun write(thread: String, error: Throwable) {
        val d = dir ?: return
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val out = StringBuilder()
        out.append(stamp).append("  thread=").append(thread).append('\n')
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth++ < 4) {
            out.append(cause.javaClass.name).append(": ").append(cause.message.orEmpty()).append('\n')
            cause = cause.cause?.takeIf { it !== cause }
        }
        // Our own frames first: they say which feature died.
        error.stackTrace.asSequence()
            .filter { it.className.startsWith("app.xani") }
            .take(MAX_FRAMES)
            .forEach { out.append("  at ").append(it).append('\n') }
        if (error.stackTrace.isNotEmpty()) {
            out.append("  top ").append(error.stackTrace.first()).append('\n')
        }
        File(d, FILE).writeText(out.toString())
    }

    /** Full trace to logcat only — useful over adb, never written to disk. */
    fun report(where: String, error: Throwable) {
        val w = StringWriter()
        error.printStackTrace(PrintWriter(w))
        Log.e(TAG, "$where\n$w")
    }
}
