package com.pomodorofocus.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages local, private image storage for custom user-selected backgrounds.
 * Copies images safely to app-private internal storage, downsamples to avoid OutOfMemory errors,
 * handles ContentProvider transient single-read constraints, and maintains zero unnecessary files or external leaks.
 */
class BackgroundImageManager(private val context: Context) {

    companion object {
        private const val TAG = "BackgroundImageManager"
        const val BACKGROUND_FILE_NAME = "custom_background.jpg"
        const val RAW_TEMP_FILE_NAME = "raw_bg_upload.tmp"
        const val MAX_IMAGE_DIMENSION = 1920
        const val EDITOR_MAX_DIMENSION = 2048
    }

    fun getBackgroundFile(): File {
        return File(context.filesDir, BACKGROUND_FILE_NAME)
    }

    fun hasCustomBackgroundFile(): Boolean {
        val file = getBackgroundFile()
        return file.exists() && file.length() > 0L
    }

    /**
     * Safely copies and downsamples an image from a content Uri into app-private storage.
     * Reads from the ContentResolver stream EXACTLY ONCE into local app-private cache to avoid
     * permission expiration, pipe exhaustion, or multi-read ContentProvider errors.
     * Returns true if successfully saved, false if file could not be read or decoded.
     */
    suspend fun saveImageFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tempRawFile = File(context.filesDir, RAW_TEMP_FILE_NAME)
        try {
            // 1. Read from ContentResolver EXACTLY ONCE and cache raw bytes locally in app-private storage
            val streamCopied = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempRawFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                true
            } ?: false

            if (!streamCopied || !tempRawFile.exists() || tempRawFile.length() <= 0L) {
                Log.e(TAG, "Failed to stream image data from URI: $uri")
                return@withContext false
            }

            // 2. Inspect image dimensions from the local cached file without large memory allocation
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(tempRawFile.absolutePath, boundsOptions)

            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) {
                Log.e(TAG, "Invalid image dimensions decoded: ${width}x${height}")
                return@withContext false
            }

            // 3. Calculate power-of-two inSampleSize to keep memory low
            var sampleSize = 1
            while ((width / sampleSize) > MAX_IMAGE_DIMENSION || (height / sampleSize) > MAX_IMAGE_DIMENSION) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val decodedBitmap = BitmapFactory.decodeFile(tempRawFile.absolutePath, decodeOptions)
            if (decodedBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from local cached file")
                return@withContext false
            }

            // 4. Handle EXIF rotation if present (e.g. camera portrait photos)
            val finalBitmap = adjustExifOrientation(tempRawFile.absolutePath, decodedBitmap)

            // 5. Save the downscaled image to permanent app-private storage
            val saved = saveBitmapInternal(finalBitmap)
            if (finalBitmap !== decodedBitmap) {
                finalBitmap.recycle()
            }
            decodedBitmap.recycle()
            saved
        } catch (e: Exception) {
            Log.e(TAG, "Error saving background from uri $uri", e)
            false
        } finally {
            if (tempRawFile.exists()) {
                tempRawFile.delete()
            }
        }
    }

    /**
     * Decodes an image chosen from the gallery into memory for the wallpaper editor.
     * Does NOT replace the current saved background; it only prepares a (downsampled,
     * EXIF-corrected) bitmap that the user can zoom, move, rotate and crop.
     */
    suspend fun decodeForEditing(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val tempFile = File(context.filesDir, "edit_bg_upload.tmp")
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied || !tempFile.exists() || tempFile.length() <= 0L) return@withContext null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > EDITOR_MAX_DIMENSION ||
                (bounds.outHeight / sampleSize) > EDITOR_MAX_DIMENSION
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(tempFile.absolutePath, options)
                ?: return@withContext null
            val oriented = adjustExifOrientation(tempFile.absolutePath, decoded)
            if (oriented !== decoded) decoded.recycle()
            oriented
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding image for editor from $uri", e)
            null
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    /**
     * Checks EXIF orientation tag and rotates bitmap if necessary.
     */
    private fun adjustExifOrientation(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check or adjust EXIF orientation", e)
            bitmap
        }
    }

    /**
     * Saves a Bitmap directly into app-private storage.
     */
    suspend fun saveBitmapDirectly(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        saveBitmapInternal(bitmap)
    }

    private fun saveBitmapInternal(bitmap: Bitmap): Boolean {
        val targetFile = getBackgroundFile()
        val tempFile = File(context.filesDir, "${BACKGROUND_FILE_NAME}.tmp")
        try {
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.flush()
            }
            if (!tempFile.exists() || tempFile.length() <= 0L) {
                return false
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                // Fallback copy if renameTo fails
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return targetFile.exists() && targetFile.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error writing bitmap to storage", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            return false
        }
    }

    /**
     * Loads the stored custom background bitmap into memory.
     * Returns null if no background is stored or if decoding fails.
     */
    suspend fun loadBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        val file = getBackgroundFile()
        if (!file.exists() || file.length() == 0L) {
            return@withContext null
        }
        try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding custom background file", e)
            null
        }
    }

    /**
     * Deletes the custom background file from local app-private storage.
     */
    suspend fun deleteCustomBackground(): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getBackgroundFile()
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting custom background file", e)
            false
        }
    }
}
