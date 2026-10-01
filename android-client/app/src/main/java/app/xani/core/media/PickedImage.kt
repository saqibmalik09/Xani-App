package app.xani.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import app.xani.core.isolate.runFeature
import java.io.ByteArrayOutputStream

/**
 * A picture the user picked, made upload-ready on a background thread: sampled
 * down while decoding (a 50-megapixel still never exists in memory at full
 * size), rotated to its EXIF orientation, centre-cropped to the exact frame the
 * server stores, and encoded once. Every picker (avatar, live cover) shares
 * this so the rules are the same everywhere. Call off the main thread.
 */
object PickedImage {
    /**
     * Decodes [uri] upright, sampled so the long edge is at least [targetLong]
     * (two frames' worth of headroom keeps the crop sharp).
     */
    fun decodeUpright(context: Context, uri: Uri, targetLong: Int): Bitmap? = runFeature(null) {
        val decoded = decode(context, uri, targetLong) ?: return@runFeature null
        orient(context, uri, decoded)
    }

    /** Largest centred window of [w]:[h] aspect, scaled to exactly [w]×[h]. */
    fun cropTo(src: Bitmap, w: Int, h: Int): Bitmap? = runFeature(null) {
        if (src.width <= 0 || src.height <= 0) return@runFeature null
        var cw = src.width
        var ch = src.height
        if (cw.toLong() * h > ch.toLong() * w) cw = ch * w / h else ch = cw * h / w
        val cropped = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
        val scaled = if (cw == w && ch == h) cropped else Bitmap.createScaledBitmap(cropped, w, h, true)
        if (scaled !== cropped) cropped.recycle()
        scaled
    }

    fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray? = runFeature(null) {
        ByteArrayOutputStream(64 * 1024).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) return@runFeature null
            out.toByteArray()
        }
    }

    private fun decode(context: Context, uri: Uri, target: Int): Bitmap? = runFeature(null) {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder applies EXIF orientation itself; orient() is a no-op there.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                decoder.setTargetSampleSize(maxOf(1, longest / target))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // createBitmap needs a software-backed source
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val opts = BitmapFactory.Options().apply { inSampleSize = maxOf(1, longest / target) }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }

    private fun orient(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) return bitmap
        val degrees = runFeature(0) {
            val tag = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            when (tag) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
        if (degrees == 0) return bitmap
        val rotated = runFeature(bitmap) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        }
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
