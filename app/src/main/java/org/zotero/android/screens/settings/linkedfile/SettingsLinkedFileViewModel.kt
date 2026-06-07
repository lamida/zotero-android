package org.zotero.android.screens.settings.linkedfile

import android.app.Application
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.Defaults
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import javax.inject.Inject

@HiltViewModel
internal class SettingsLinkedFileViewModel @Inject constructor(
    private val application: Application,
    private val defaults: Defaults,
) : BaseViewModel2<SettingsLinkedFileViewState, SettingsLinkedFileViewEffect>(SettingsLinkedFileViewState()) {

    fun init() = initOnce {
        updateState {
            copy(
                desktopBasePath = defaults.getLinkedFileDesktopBasePath() ?: "",
                androidBaseUri = defaults.getLinkedFileAndroidBaseUri(),
            )
        }
    }

    fun setDesktopBasePath(path: String) {
        defaults.setLinkedFileDesktopBasePath(path.ifBlank { null })
        updateState { copy(desktopBasePath = path) }
    }

    fun onAndroidFolderPicked(uri: Uri?) {
        if (uri == null) return
        application.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uriString = uri.toString()
        defaults.setLinkedFileAndroidBaseUri(uriString)
        updateState { copy(androidBaseUri = uriString) }
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
}

internal data class SettingsLinkedFileViewState(
    val desktopBasePath: String = "",
    val androidBaseUri: String? = null,
) : ViewState

internal sealed class SettingsLinkedFileViewEffect : ViewEffect {
    object OnBack : SettingsLinkedFileViewEffect()
}
