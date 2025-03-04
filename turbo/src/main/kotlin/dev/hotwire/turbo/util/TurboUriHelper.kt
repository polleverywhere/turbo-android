package dev.hotwire.turbo.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal class TurboUriHelper(val context: Context) {
    @Suppress("BlockingMethodInNonBlockingContext") // https://youtrack.jetbrains.com/issue/KT-39684
    suspend fun writeFileTo(uri: Uri, destDirectory: File): File? {
        val uriAttributes = getAttributes(uri) ?: return null
        val file = File(destDirectory, uriAttributes.fileName)

        if (file.hasPathTraversalVulnerability(destDirectory)) {
            return null
        }

        return withContext(dispatcherProvider.io) {
            try {
                if (file.exists()) {
                    file.delete()
                }

                context.contentResolver.openInputStream(uri).use {
                    val outputStream = file.outputStream()
                    it?.copyTo(outputStream)
                    outputStream.close()
                }
                file
            } catch (e: Exception) {
                TurboLog.e("${e.message}")
                null
            }
        }
    }

    fun getAttributes(uri: Uri): TurboUriAttributes? {
        return when (uri.scheme) {
            "file" -> getFileUriAttributes(uri)
            "content" -> getContentUriAttributes(context, uri)
            else -> null
        }
    }

    private fun getFileUriAttributes(uri: Uri): TurboUriAttributes? {
        val file = uri.getFile() ?: return null

        if (file.originIsAppResource()) {
            return null
        }

        return TurboUriAttributes(
            fileName = file.name,
            mimeType = uri.mimeType(),
            fileSize = file.length()
        )
    }

    private fun getContentUriAttributes(context: Context, uri: Uri): TurboUriAttributes? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val mimeType: String? = context.contentResolver.getType(uri)
        val cursor = try {
            context.contentResolver.query(uri, projection, null, null, null)
        } catch (ignored: Throwable) {
            null
        }

        val cursorAttributes = cursor?.use {
            when (it.moveToFirst()) {
                true -> uriAttributesFromContentQuery(uri, mimeType, it)
                else -> null
            }
        }

        return cursorAttributes ?: uriAttributesDerivedFromUri(uri, mimeType)
    }

    private fun uriAttributesFromContentQuery(uri: Uri, mimeType: String?, cursor: Cursor): TurboUriAttributes? {
        val columnName = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
        val columnSize = cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }
        val fileName: String? = columnName?.let { cursor.getString(it) }
        val fileSize: Long? = columnSize?.let { cursor.getLong(it) }

        if (fileName == null && mimeType == null) {
            return null
        }

        return TurboUriAttributes(
            fileName = fileName ?: "attachment",
            mimeType = mimeType ?: uri.mimeType(),
            fileSize = fileSize ?: 0L
        )
    }

    private fun uriAttributesDerivedFromUri(uri: Uri, mimeType: String?): TurboUriAttributes? {
        val fileName: String? = uri.lastPathSegment

        if (fileName == null && mimeType == null) {
            return null
        }

        return TurboUriAttributes(
            fileName = fileName ?: "attachment",
            mimeType = mimeType ?: uri.mimeType(),
            fileSize = 0
        )
    }

    /**
     * Checks for path traversal vulnerability (caused e.g. by input filename containing "../")
     * which could lead to modification of the destination directory.
     *
     * More information: https://developer.android.com/topic/security/risks/path-traversal
     */
    private fun File.hasPathTraversalVulnerability(destDirectory: File): Boolean {
        return try {
            val destinationDirectoryPath = destDirectory.canonicalPath
            val outputFilePath = this.canonicalPath

            !outputFilePath.startsWith(destinationDirectoryPath)
        } catch (e: Exception) {
            TurboLog.e("${e.message}")
            false
        }
    }
 
    /**
     * Determine if the file points to an app resource. Symbolic link
     * attacks can target app resource files to steal private data.
     */
    private fun File.originIsAppResource(): Boolean {
        return try {
            canonicalPath.contains(context.packageName)
        } catch (e: IOException) {
            TurboLog.e("${e.message}")
            false
        }
    }

    private fun Uri.fileExtension(): String? {
        return lastPathSegment?.extract("\\.([0-9a-z]+)$")
    }

    private fun Uri?.mimeType(): String {
        return this?.fileExtension()?.let {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
        } ?: "application/octet-stream"
    }

    private fun Uri.getFile(): File? {
        return path?.let { File(it) }
    }
}
