package org.zotero.android.screens.settings.linkedfile

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.junit.Test

class SettingsLinkedFileViewModelTest {

    @Test
    fun `computeCommonBase returns shared directory prefix for multi-author paths`() {
        val paths = listOf(
            "/Users/jon/Dropbox/zotero/Adams/paper.pdf",
            "/Users/jon/Dropbox/zotero/Baker/article.pdf",
            "/Users/jon/Dropbox/zotero/Chen/thesis.pdf",
        )
        SettingsLinkedFileViewModel.computeCommonBase(paths) shouldBeEqualTo "/Users/jon/Dropbox/zotero"
    }

    @Test
    fun `computeCommonBase handles flat structure (files at root of zotero folder)`() {
        val paths = listOf(
            "/Users/jon/Dropbox/zotero/paper1.pdf",
            "/Users/jon/Dropbox/zotero/paper2.pdf",
        )
        SettingsLinkedFileViewModel.computeCommonBase(paths) shouldBeEqualTo "/Users/jon/Dropbox/zotero"
    }

    @Test
    fun `computeCommonBase returns single directory for one path`() {
        val result = SettingsLinkedFileViewModel.computeCommonBase(
            listOf("/Users/jon/Dropbox/zotero/Adams/paper.pdf")
        )
        result shouldBeEqualTo "/Users/jon/Dropbox/zotero/Adams"
    }

    @Test
    fun `computeCommonBase returns null for empty list`() {
        SettingsLinkedFileViewModel.computeCommonBase(emptyList()).shouldBeNull()
    }

    @Test
    fun `computeCommonBase normalises Windows backslashes`() {
        val paths = listOf(
            "C:\\Users\\jon\\Dropbox\\zotero\\Adams\\paper.pdf",
            "C:\\Users\\jon\\Dropbox\\zotero\\Baker\\article.pdf",
        )
        SettingsLinkedFileViewModel.computeCommonBase(paths) shouldBeEqualTo "C:/Users/jon/Dropbox/zotero"
    }

    @Test
    fun `computeCommonBase handles mixed depth paths`() {
        val paths = listOf(
            "/base/zotero/Adams/2024/paper.pdf",
            "/base/zotero/top-level.pdf",
        )
        SettingsLinkedFileViewModel.computeCommonBase(paths) shouldBeEqualTo "/base/zotero"
    }

    @Test
    fun `computeCommonBase returns null when paths share no common directory`() {
        val paths = listOf(
            "/Users/alice/zotero/paper.pdf",
            "/Users/bob/zotero/paper.pdf",
        )
        val result = SettingsLinkedFileViewModel.computeCommonBase(paths)
        result shouldBeEqualTo "/Users"
    }
}
