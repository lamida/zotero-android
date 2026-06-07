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
     * Resolves a desktop-absolute file path to a SAF document URI on Android.
     *
     * Strategy: strip the desktop base prefix from the stored path, then append
     * the remaining relative path to the SAF tree URI.
     *
     * Example:
     *   desktopPath  = "/Users/jon/Google Drive/My Drive/zotero/Adams/paper.pdf"
     *   desktopBase  = "/Users/jon/Google Drive/My Drive/zotero"
     *   androidBase  = content://com.android.externalstorage.documents/tree/...
     *   result URI   = androidBase / Adams / paper.pdf
     */
    fun resolveUri(desktopPath: String): Uri? {
        val androidBaseUriString = defaults.getLinkedFileAndroidBaseUri() ?: return null
        val desktopBase = defaults.getLinkedFileDesktopBasePath() ?: return null

        val relativePath = computeRelativePath(desktopPath, desktopBase) ?: return null

        return try {
            val treeUri = Uri.parse(androidBaseUriString)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val fileDocId = "$rootDocId/$relativePath"
            DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
        } catch (e: Exception) {
            Timber.e(e, "LinkedFileResolver: failed to build URI for path '$desktopPath'")
            null
        }
    }

    companion object {
        /** Strips [desktopBase] from [desktopPath] and returns the remaining relative path, or null if the path does not start with the base. */
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
}
