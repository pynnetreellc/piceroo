package com.piceroo.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** One photo or video on the phone, as Android's MediaStore describes it. */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val dateTaken: Long, // epoch millis
    val size: Long, // bytes
    val bucket: String, // folder name, e.g. "Camera", "Screenshots"
    val relativePath: String, // e.g. "DCIM/Camera/"
    val isVideo: Boolean,
    val durationMs: Long,
)

/**
 * Reads every photo and video on the device. MediaStore is Android's index of media files;
 * querying it is like a SQL SELECT against a table where each row is one file.
 * Items already in the phone's trash are skipped automatically.
 */
class MediaRepository(private val context: Context) {

    fun loadAll(): List<MediaItem> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DURATION,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val durCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                // Trash requests need the Images/Video URI, not the generic Files one.
                val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                // DATE_TAKEN is missing on some files; DATE_ADDED is in seconds, not millis.
                val taken = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(addedCol) * 1000)

                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    name = c.getString(nameCol) ?: "",
                    mimeType = c.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*",
                    dateTaken = taken,
                    size = c.getLong(sizeCol),
                    bucket = c.getString(bucketCol) ?: "",
                    relativePath = c.getString(pathCol) ?: "",
                    isVideo = isVideo,
                    durationMs = c.getLong(durCol),
                )
            }
        }
        return items
    }
}
