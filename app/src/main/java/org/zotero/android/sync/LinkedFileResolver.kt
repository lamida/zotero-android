package org.zotero.android.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zotero.android.architecture.Defaults
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkedFileResolver @Inject constructor(
    private val context: Context,
    private val defaults: Defaults,
) {

    /**
     * Resolves a desktop-absolute file path to a SAF document URI by traversing
     * the selected tree one segment at a time. Works with any documents provider
     * (external storage, Google Drive, Syncthing, etc.).
     */
    fun resolveUri(desktopPath: String): Uri? {
        val androidBaseUriString = defaults.getLinkedFileAndroidBaseUri() ?: return null
        val desktopBase = defaults.getLinkedFileDesktopBasePath() ?: return null

        val relativePath = computeRelativePath(desktopPath, desktopBase) ?: return null
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        return try {
            val treeUri = Uri.parse(androidBaseUriString)
            traverseTree(treeUri, segments)
        } catch (e: Exception) {
            Timber.e(e, "LinkedFileResolver: failed to resolve URI for '$desktopPath'")
            null
        }
    }

    /** Walks the SAF tree one path segment at a time, returning the URI of the final node. */
    private fun traverseTree(treeUri: Uri, segments: List<String>): Uri? {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        var currentDocId = rootDocId
        val cr = context.contentResolver

        for (segment in segments) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
            val childDocId = cr.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (name == segment) return@use cursor.getString(0)
                }
                null
            } ?: return null
            currentDocId = childDocId
        }

        return DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
    }

    fun exists(desktopPath: String): Boolean {
        val uri = resolveUri(desktopPath) ?: return false
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { cursor -> cursor.moveToFirst() } ?: false
        } catch (e: Exception) {
            Timber.w(e, "LinkedFileResolver: exists check failed for '$desktopPath'")
            false
        }
    }

    suspend fun copyToTempFile(desktopPath: String, destFile: File) = withContext(Dispatchers.IO) {
        val uri = resolveUri(desktopPath)
            ?: throw IllegalStateException("Cannot resolve linked file: $desktopPath")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open linked file stream: $desktopPath")
    }

    companion object {
        /** Strips [desktopBase] from [desktopPath] and returns the remaining relative path. */
        internal fun computeRelativePath(desktopPath: String, desktopBase: String): String? {
            val normalizedBase = desktopBase.trimEnd('/')
            val normalizedPath = desktopPath.replace('\\', '/')
            if (!normalizedPath.startsWith(normalizedBase)) {
                Timber.w("LinkedFileResolver: path '$desktopPath' does not start with base '$desktopBase'")
                return null
            }
            val relative = normalizedPath.removePrefix(normalizedBase).trimStart('/')
            return if (relative.isEmpty()) null else relative
        }
    }
}
