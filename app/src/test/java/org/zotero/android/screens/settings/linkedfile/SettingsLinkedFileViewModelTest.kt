package org.zotero.android.screens.settings.linkedfile

import org.junit.Test

// Desktop base path detection was removed — linked file paths use the "attachments:" relative
// format by default (ZotMoov behaviour). No ViewModel-level logic remains to test here.
class SettingsLinkedFileViewModelTest {

    @Test
    fun `placeholder — settings linked file viewmodel has no testable pure logic`() {
        // The ViewModel's only behaviour is persisting the SAF URI to SharedPreferences
        // and delegating to the OS folder picker, both of which require Android instrumentation.
    }
}
