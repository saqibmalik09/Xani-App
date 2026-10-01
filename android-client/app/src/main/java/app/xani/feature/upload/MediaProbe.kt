package app.xani.feature.upload

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import app.xani.core.isolate.runFeature
import app.xani.core.net.MediaConfig
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** What the device knows about a file before a byte is uploaded. */
data class MediaInfo(
    val uri: Uri,
    val kind: String, // video | image
    val mime: String,
    val size: Long,
    val width: Int, // display orientation applied
    val height: Int,
    val durationMs: Int,
    val capturedAt: String?, // RFC3339 UTC or null
) {
    val isVideo: Boolean get() = kind == "video"
    val aspect: Float get() = if (height == 0) 0f else width.toFloat() / height

    /** True for the feed's native 9:16 (±3%). */
    val isPortrait916: Boolean get() = aspect in 0.545f..0.58f
}

/** Why a file cannot be posted. `null` means it can. */
enum class MediaProblem { Unreadable, Unsupported, TooLong, TooLarge, BadAspect }

/**
 * Reads container metadata on a background thread. Nothing here decodes a
 * frame: video facts come from the container index, image bounds from the
 * header, dates from EXIF or the container's creation tag.
 */
object MediaProbe {
    private val videoMimes = setOf("video/mp4", "video/quicktime", "video/webm", "video/3gpp", "video/x-matroska")
    private val imageMimes = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

    /** Must be called off the main thread. */
    fun probe(context: Context, uri: Uri): MediaInfo? = runFeature(null) {
        val resolver = context.contentResolver
        val mime = (resolver.getType(uri) ?: guessMime(uri)).lowercase(Locale.ROOT)
        val size = sizeOf(context, uri)
        when {
            mime.startsWith("video/") -> probeVideo(context, uri, mime, size)
            mime.startsWith("image/") -> probeImage(context, uri, mime, size)
            else -> null
        }
    }

    /** Applies the server's rules locally so a 12-minute clip is refused instantly. */
    fun check(info: MediaInfo): MediaProblem? {
        if (info.size <= 0 || info.width <= 0 || info.height <= 0) return MediaProblem.Unreadable
        if (info.isVideo) {
            if (info.mime !in videoMimes) return MediaProblem.Unsupported
            if (info.durationMs > MediaConfig.maxVideoSeconds * 1000L + 500) return MediaProblem.TooLong
            if (info.size > MediaConfig.maxVideoBytes) return MediaProblem.TooLarge
        } else {
            if (info.mime !in imageMimes) return MediaProblem.Unsupported
            if (info.size > MediaConfig.maxImageBytes) return MediaProblem.TooLarge
        }
        val ratio = info.aspect
        if (ratio < 1f / 2.6f || ratio > 2.6f) return MediaProblem.BadAspect
        return null
    }

    private fun probeVideo(context: Context, uri: Uri, mime: String, size: Long): MediaInfo? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot % 180 != 0) w = h.also { h = w }
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val date = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            MediaInfo(
                uri = uri, kind = "video", mime = mime, size = size, width = w, height = h,
                durationMs = dur.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                capturedAt = parseContainerDate(date),
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun probeImage(context: Context, uri: Uri, mime: String, size: Long): MediaInfo? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var w = bounds.outWidth
        var h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var captured: String? = null
        context.contentResolver.openInputStream(uri)?.use { input ->
            runCatching {
                val exif = ExifInterface(input)
                val o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                if (o == ExifInterface.ORIENTATION_ROTATE_90 || o == ExifInterface.ORIENTATION_ROTATE_270 ||
                    o == ExifInterface.ORIENTATION_TRANSPOSE || o == ExifInterface.ORIENTATION_TRANSVERSE
                ) {
                    w = h.also { h = w }
                }
                captured = parseExifDate(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                )
            }
        }
        return MediaInfo(uri, "image", mime, size, w, h, 0, captured)
    }

    private fun sizeOf(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) {
                val s = c.getLong(0)
                if (s > 0) return s
            }
        }
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }

    private fun guessMime(uri: Uri): String {
        val name = uri.lastPathSegment.orEmpty().lowercase(Locale.ROOT)
        return when {
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".mov") -> "video/quicktime"
            name.endsWith(".webm") -> "video/webm"
            name.endsWith(".3gp") -> "video/3gpp"
            name.endsWith(".mkv") -> "video/x-matroska"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".heic") -> "image/heic"
            else -> "application/octet-stream"
        }
    }

    private val rfc3339 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Container dates look like "20260921T105501.000Z". */
    private fun parseContainerDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val f = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return runCatching { f.parse(raw.substring(0, 15))?.let(rfc3339::format) }.getOrNull()
    }

    /** EXIF dates are local time "yyyy:MM:dd HH:mm:ss" with no zone. */
    private fun parseExifDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val f = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        return runCatching { f.parse(raw)?.let(rfc3339::format) }.getOrNull()
    }
}
