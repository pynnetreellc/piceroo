package com.piceroo.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
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
 * Reads photos and videos from the device. MediaStore is Android's index of media files;
 * querying it is like a SQL SELECT against a table where each row is one file.
 */
class MediaRepository(private val context: Context) {

    /** Every photo and video, skipping anything in Android's trash. */
    fun loadAll(): List<MediaItem> = query(MediaStore.MATCH_EXCLUDE)

    /**
     * Photos sitting in Android's trash that Piceroo put there (earlier builds trashed instead
     * of deleting). [deletedKeys] are (file name, size) pairs from Piceroo's own log, so we
     * never touch things other apps trashed.
     */
    fun loadTrashed(deletedKeys: Set<Pair<String, Long>>): List<MediaItem> {
        if (deletedKeys.isEmpty()) return emptyList()
        // Android renames trashed files to ".trashed-<expiry>-<original name>".
        val prefix = Regex("""^\.trashed-\d+-""")
        return query(MediaStore.MATCH_ONLY).filter { (it.name.replace(prefix, "") to it.size) in deletedKeys }
    }

    /** [matchTrashed] is MATCH_EXCLUDE (normal files) or MATCH_ONLY (only trashed files). */
    private fun query(matchTrashed: Int): List<MediaItem> {
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
        // The Bundle form of query() is the only way to ask about trashed files.
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                ),
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, matchTrashed)
        }

        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(collection, projection, args, null)?.use { c ->
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
                // Delete requests need the Images/Video URI, not the generic Files one.
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
