package org.zotero.android.screens.settings.linkedfile

import android.app.Application
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.Defaults
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
internal class SettingsLinkedFileViewModel @Inject constructor(
    private val application: Application,
    private val defaults: Defaults,
) : BaseViewModel2<SettingsLinkedFileViewState, SettingsLinkedFileViewEffect>(SettingsLinkedFileViewState()) {

    fun init() = initOnce {
        updateState {
            copy(
                androidBaseUri = defaults.getLinkedFileAndroidBaseUri(),
                openExternally = defaults.openLinkedPdfExternally(),
            )
        }
    }

    fun onOpenExternallyToggled(enabled: Boolean) {
        defaults.setOpenLinkedPdfExternally(enabled)
        updateState { copy(openExternally = enabled) }
    }

    fun onAndroidFolderPicked(uri: Uri?) {
        if (uri == null) return
        Timber.i("SettingsLinkedFile: folder picked $uri")
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
    val androidBaseUri: String? = null,
    val openExternally: Boolean = false,
) : ViewState

internal sealed class SettingsLinkedFileViewEffect : ViewEffect {
    object OnBack : SettingsLinkedFileViewEffect()
}
