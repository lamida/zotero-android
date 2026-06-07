package org.zotero.android.screens.settings.linkedfile

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.Defaults
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import org.zotero.android.database.DbWrapperMain
import org.zotero.android.database.objects.FieldKeys
import org.zotero.android.database.objects.RItem
import org.zotero.android.sync.LinkMode
import javax.inject.Inject

@HiltViewModel
internal class SettingsLinkedFileViewModel @Inject constructor(
    private val application: Application,
    private val defaults: Defaults,
    private val dbWrapperMain: DbWrapperMain,
) : BaseViewModel2<SettingsLinkedFileViewState, SettingsLinkedFileViewEffect>(SettingsLinkedFileViewState()) {

    fun init() = initOnce {
        val savedBase = defaults.getLinkedFileDesktopBasePath()
        updateState {
            copy(
                desktopBasePath = savedBase ?: "",
                androidBaseUri = defaults.getLinkedFileAndroidBaseUri(),
            )
        }
        if (savedBase == null) {
            viewModelScope.launch {
                val detected = detectDesktopBase()
                if (detected != null) {
                    defaults.setLinkedFileDesktopBasePath(detected)
                    updateState { copy(desktopBasePath = detected, isBasePathAutoDetected = true) }
                }
            }
        }
    }

    fun setDesktopBasePath(path: String) {
        defaults.setLinkedFileDesktopBasePath(path.ifBlank { null })
        updateState { copy(desktopBasePath = path, isBasePathAutoDetected = false) }
    }

    fun onAndroidFolderPicked(uri: Uri?) {
        if (uri == null) return
        application.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uriString = uri.toString()
        defaults.setLinkedFileAndroidBaseUri(uriString)
        updateState { copy(androidBaseUri = uriString) }
        if (defaults.getLinkedFileDesktopBasePath() == null) {
            viewModelScope.launch {
                val detected = detectDesktopBase()
                if (detected != null) {
                    defaults.setLinkedFileDesktopBasePath(detected)
                    updateState { copy(desktopBasePath = detected, isBasePathAutoDetected = true) }
                }
            }
        }
    }

    fun clearAndroidFolder() {
        val existing = defaults.getLinkedFileAndroidBaseUri()
        if (existing != null) {
            try {
                application.contentResolver.releasePersistableUriPermission(
                    Uri.parse(existing),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        defaults.setLinkedFileAndroidBaseUri(null)
        updateState { copy(androidBaseUri = null) }
    }

    fun navigateBack() {
        triggerEffect(SettingsLinkedFileViewEffect.OnBack)
    }

    private suspend fun detectDesktopBase(): String? = withContext(Dispatchers.IO) {
        try {
            var result: String? = null
            dbWrapperMain.realmDbStorage.perform { coordinator ->
                val paths = coordinator.realm
                    .where(RItem::class.java)
                    .equalTo("rawType", "attachment")
                    .findAll()
                    .mapNotNull { item ->
                        val lm = item.fields.firstOrNull { it.key == FieldKeys.Item.Attachment.linkMode }?.value
                        if (LinkMode.from(lm ?: "") != LinkMode.linkedFile) return@mapNotNull null
                        item.fields.firstOrNull { it.key == FieldKeys.Item.Attachment.path }?.value
                            ?.takeIf { it.isNotEmpty() }
                    }
                result = computeCommonBase(paths)
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Computes the deepest common directory prefix across a list of absolute file paths. */
        internal fun computeCommonBase(paths: List<String>): String? {
            if (paths.isEmpty()) return null
            val dirs = paths.map { path ->
                val normalized = path.replace('\\', '/')
                val lastSlash = normalized.lastIndexOf('/')
                if (lastSlash > 0) normalized.substring(0, lastSlash) else normalized
            }
            if (dirs.size == 1) return dirs.first()
            val parts = dirs.map { it.split('/').filter { p -> p.isNotEmpty() } }
            val shortest = parts.minByOrNull { it.size } ?: return null
            val common = mutableListOf<String>()
            for (i in shortest.indices) {
                val segment = shortest[i]
                if (parts.all { it.getOrNull(i) == segment }) common.add(segment) else break
            }
            if (common.isEmpty()) return null
            val leading = if (paths.first().startsWith('/')) "/" else ""
            return leading + common.joinToString("/")
        }
    }
}

internal data class SettingsLinkedFileViewState(
    val desktopBasePath: String = "",
    val androidBaseUri: String? = null,
    val isBasePathAutoDetected: Boolean = false,
) : ViewState

internal sealed class SettingsLinkedFileViewEffect : ViewEffect {
    object OnBack : SettingsLinkedFileViewEffect()
}
