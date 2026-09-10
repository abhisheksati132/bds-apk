package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageCompressorHelper {

    private const val MAX_DIMENSION = 1600
    private const val COMPRESSION_QUALITY = 82

    /**
     * Compresses the image at [imageUri] to a high-quality, lightweight file (~100KB-250KB)
     * suitable for rapid transmission over cellular networks.
     */
    suspend fun compressImage(context: Context, imageUri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            // 1. Decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return@withContext null

            // 2. Calculate sample size
            var inSampleSize = 1
            var maxDim = maxOf(origWidth, origHeight)
            while (maxDim / 2 >= MAX_DIMENSION) {
                inSampleSize *= 2
                maxDim /= 2
            }

            // 3. Decode bitmap with inSampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap: Bitmap? = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            // 4. Correct EXIF Orientation if needed
            bitmap = correctExifOrientation(context, imageUri, bitmap!!)

            // 5. Compress to temporary file
            val tempFile = File(context.cacheDir, "compressed_${UUID.randomUUID()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, out)
            }
            bitmap.recycle()

            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun correctExifOrientation(context: Context, imageUri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                inputStream.close()

                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    else -> return bitmap
                }

                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    /**
     * Compresses the image to a compact Base64 Data URI (~30KB-60KB)
     * for instant, 100% reliable in-band Firestore message transmission.
     */
    suspend fun compressImageToBase64(context: Context, imageUri: Uri, maxDimension: Int = 800, quality: Int = 70): String? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return@withContext null

            var inSampleSize = 1
            var maxDim = maxOf(origWidth, origHeight)
            while (maxDim / 2 >= maxDimension) {
                inSampleSize *= 2
                maxDim /= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap: Bitmap? = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            bitmap = correctExifOrientation(context, imageUri, bitmap!!)
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            bitmap.recycle()
            val bytes = baos.toByteArray()
            val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64Str"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes a Base64 data URI (or raw base64) into a local cached file.
     */
    suspend fun decodeBase64ToCache(context: Context, dataUriOrBase64: String, prefix: String = "media_", ext: String = ".jpg"): File? = withContext(Dispatchers.IO) {
        try {
            val base64Data = if (dataUriOrBase64.contains(",")) {
                dataUriOrBase64.substringAfter(",")
            } else {
                dataUriOrBase64
            }
            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            val cacheDir = File(context.cacheDir, "received_media").apply { if (!exists()) mkdirs() }
            val file = File(cacheDir, "${prefix}${UUID.randomUUID()}$ext")
            FileOutputStream(file).use { it.write(bytes) }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
