package org.zotero.android.sync

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.Test

class LinkedFileResolverTest {

    // --- isRelativePath ---

    @Test
    fun `isRelativePath returns true for attachments prefix`() {
        LinkedFileResolver.isRelativePath("attachments:Adams/paper.pdf").shouldBeTrue()
    }

    @Test
    fun `isRelativePath returns false for absolute path`() {
        LinkedFileResolver.isRelativePath("/Users/jon/Dropbox/zotero/Adams/paper.pdf")
            .shouldBeEqualTo(false)
    }

    // --- computeRelativePath (absolute paths) ---

    @Test
    fun `computeRelativePath strips base prefix and returns relative path`() {
        val result = LinkedFileResolver.computeRelativePath(
            desktopPath = "/Users/jon/Dropbox/zotero/Adams/paper.pdf",
            desktopBase = "/Users/jon/Dropbox/zotero"
        )
        result shouldBeEqualTo "Adams/paper.pdf"
    }

    @Test
    fun `computeRelativePath handles trailing slash on base`() {
        val result = LinkedFileResolver.computeRelativePath(
            desktopPath = "/Users/jon/Dropbox/zotero/Smith/article.pdf",
            desktopBase = "/Users/jon/Dropbox/zotero/"
        )
        result shouldBeEqualTo "Smith/article.pdf"
    }

    @Test
    fun `computeRelativePath returns null when path does not start with base`() {
        val result = LinkedFileResolver.computeRelativePath(
            desktopPath = "/other/path/file.pdf",
            desktopBase = "/Users/jon/Dropbox/zotero"
        )
        result.shouldBeNull()
    }

    @Test
    fun `computeRelativePath returns null for path equal to base`() {
        val result = LinkedFileResolver.computeRelativePath(
            desktopPath = "/Users/jon/Dropbox/zotero",
            desktopBase = "/Users/jon/Dropbox/zotero"
        )
        result.shouldBeNull()
    }

    @Test
    fun `computeRelativePath normalises Windows backslashes`() {
        val result = LinkedFileResolver.computeRelativePath(
            desktopPath = "/Users/jon/Dropbox/zotero\\Adams\\paper.pdf",
            desktopBase = "/Users/jon/Dropbox/zotero"
        )
        result shouldBeEqualTo "Adams/paper.pdf"
    }

    @Test
    fun `computeRelativePath handles deep nested path`() {
        val result = LinkedFileResolver.computeRelativePath(
            desktopPath = "/Users/jon/Google Drive/My Drive/zotero/Adams 2024/notes/chapter1.pdf",
            desktopBase = "/Users/jon/Google Drive/My Drive/zotero"
        )
        result shouldBeEqualTo "Adams 2024/notes/chapter1.pdf"
    }

    // --- attachments: relative path handling ---

    @Test
    fun `isRelativePath detects ZotMoov relative path`() {
        LinkedFileResolver.isRelativePath(
            "attachments:Afroozeh/Afroozeh et al. - 2023 - ALP.pdf"
        ).shouldBeTrue()
    }

    @Test
    fun `isRelativePath false for non-attachments prefix`() {
        LinkedFileResolver.isRelativePath("Afroozeh/paper.pdf").shouldBeEqualTo(false)
    }
}
