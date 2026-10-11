package com.charan.padstudio.pen

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import androidx.core.content.FileProvider

/**
 * FileProvider that also answers the document columns DocumentFile asks for. Key Mapper's
 * restore screen opens the shared backup with DocumentFile.fromSingleUri and copies it with the
 * SimpleStorage library; with a plain FileProvider the mime_type query comes back empty, so the
 * file doesn't look like a file and the restore fails with a bare "Error".
 */
class ProfileFileProvider : FileProvider() {
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        var name: String? = null
        var size = 0L
        super.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null).use { base ->
            if (base.moveToFirst()) {
                name = base.getString(0)
                size = base.getLong(1)
            }
        }
        val columns = projection?.toList()?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, Document.COLUMN_MIME_TYPE)
        val row: List<Any?> = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> name // same column name as Document.COLUMN_DISPLAY_NAME
                OpenableColumns.SIZE -> size // same as Document.COLUMN_SIZE
                Document.COLUMN_MIME_TYPE -> getType(uri)
                Document.COLUMN_DOCUMENT_ID -> uri.lastPathSegment
                Document.COLUMN_FLAGS -> 0
                Document.COLUMN_LAST_MODIFIED -> System.currentTimeMillis()
                else -> null
            }
        }
        return MatrixCursor(columns).apply { addRow(row.toTypedArray<Any?>()) }
    }
}
