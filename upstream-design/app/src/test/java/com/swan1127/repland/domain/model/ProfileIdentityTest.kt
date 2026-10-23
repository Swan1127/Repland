package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * The nickname, the initials, and "is this actually a picture" — the three questions a screenshot
 * cannot answer, which is why they live in a pure module and get real tests.
 *
 * Two of these tests are not about one case each but about a whole mistake class:
 *  - `sanitizeNickname` must collapse and trim so a copy-paste " 小 茂 " never breaks the centred
 *    header (a two-line nickname would), and must cap length — a header that scrolls is broken;
 *  - `sniffImageFormat` must reject a `.jpg` whose bytes are text, because a MIME-only check is
 *    exactly what accepts that file and then lets the decoder fail on it.
 */
class ProfileIdentityTest {

    // --- sanitizeNickname -------------------------------------------------

    @Test
    fun `drops newlines and control characters`() {
        val raw = "小\n茂\u0000\t小"
        assertEquals("小茂小", ProfileIdentity.sanitizeNickname(raw))
    }

    @Test
    fun `collapses internal whitespace and trims`() {
        assertEquals("小 茂", ProfileIdentity.sanitizeNickname("  小   茂  "))
    }

    @Test
    fun `returns empty when only whitespace or control remains`() {
        assertEquals("", ProfileIdentity.sanitizeNickname("   \n\t\u000B "))
    }

    @Test
    fun `caps at the maximum character count`() {
        val twentyOne = "a".repeat(21)
        assertEquals("a".repeat(ProfileIdentity.NICKNAME_MAX_CHARS), ProfileIdentity.sanitizeNickname(twentyOne))
    }

    // --- displayName ------------------------------------------------------

    @Test
    fun `displayName prefers the chosen nickname`() {
        assertEquals("小茂", ProfileIdentity.displayName("小茂", "学习者"))
    }

    @Test
    fun `displayName falls back when nickname blank`() {
        assertEquals("Learner", ProfileIdentity.displayName("   ", "Learner"))
    }

    @Test
    fun `displayName is empty only when both blank`() {
        assertEquals("", ProfileIdentity.displayName("", "   "))
    }

    // --- initials ---------------------------------------------------------

    @Test
    fun `initials of two latin words takes first letter of each`() {
        assertEquals("AC", ProfileIdentity.initials("Alice Chen"))
    }

    @Test
    fun `initials of a single ascii word takes one letter`() {
        assertEquals("A", ProfileIdentity.initials("Alice"))
    }

    @Test
    fun `initials of a single cjk token takes two characters`() {
        assertEquals("小茂", ProfileIdentity.initials("小茂"))
    }

    @Test
    fun `initials of an empty name is empty`() {
        assertEquals("", ProfileIdentity.initials("   "))
    }

    @Test
    fun `initials of a single cjk character keeps the one character`() {
        assertEquals("小", ProfileIdentity.initials("小"))
    }

    // --- sniffImageFormat ------------------------------------------------

    private fun headerOf(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()

    @Test
    fun `sniffs png`() {
        assertEquals(ImageFormat.PNG, ProfileIdentity.sniffImageFormat(headerOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
    }

    @Test
    fun `sniffs jpeg`() {
        assertEquals(ImageFormat.JPEG, ProfileIdentity.sniffImageFormat(headerOf(0xFF, 0xD8, 0xFF, 0xE0)))
    }

    @Test
    fun `sniffs gif`() {
        assertEquals(ImageFormat.GIF, ProfileIdentity.sniffImageFormat(headerOf(0x47, 0x49, 0x46, 0x38)))
    }

    @Test
    fun `sniffs webp`() {
        assertEquals(ImageFormat.WEBP, ProfileIdentity.sniffImageFormat(headerOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)))
    }

    @Test
    fun `rejects too short input`() {
        assertNull(ProfileIdentity.sniffImageFormat(headerOf(0x89, 0x50)))
    }

    @Test
    fun `rejects non-image bytes`() {
        assertNull(ProfileIdentity.sniffImageFormat("hello world".toByteArray()))
    }

    // --- avatarRejection --------------------------------------------------

    @Test
    fun `rejects non-image mime`() {
        assertEquals(AvatarRejection.NOT_AN_IMAGE, ProfileIdentity.avatarRejection(10, "text/plain"))
    }

    @Test
    fun `rejects over the size cap`() {
        assertEquals(AvatarRejection.TOO_LARGE, ProfileIdentity.avatarRejection(ProfileIdentity.AVATAR_MAX_BYTES + 1, "image/png"))
    }

    @Test
    fun `does not reject unknown mime or unstated size`() {
        assertNull(ProfileIdentity.avatarRejection(-1, null))
        assertNull(ProfileIdentity.avatarRejection(1024, null))
    }

    // --- copyAvatar / saveAvatar -----------------------------------------

    /** A real 1x1 PNG (the canonical 67-byte file), so the byte sniff accepts it. */
    private val png1x1 = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(),
        0x1A.toByte(), 0x0A.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(),
        0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x08.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1F.toByte(),
        0x15.toByte(), 0xC4.toByte(), 0x89.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x0D.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(), 0x54.toByte(), 0x78.toByte(),
        0x9C.toByte(), 0x63.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x05.toByte(), 0x00.toByte(), 0x01.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x2D.toByte(),
        0xB4.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(),
        0x45.toByte(), 0x4E.toByte(), 0x44.toByte(), 0xAE.toByte(), 0x42.toByte(), 0x60.toByte(),
        0x82.toByte(),
    )

    @Test
    fun `saves a valid image and reports its path with the right extension`() {
        val dir = createTempDir("avatar")
        val result = ProfileIdentity.saveAvatar(dir, ByteArrayInputStream(png1x1))
        assertTrue(result is AvatarSaveResult.Saved)
        val path = (result as AvatarSaveResult.Saved).path
        assertTrue(path.endsWith("avatar.png"))
        assertTrue(java.io.File(path).exists())
    }

    @Test
    fun `rejects non-image bytes when saving`() {
        val dir = createTempDir("avatar")
        val result = ProfileIdentity.saveAvatar(dir, ByteArrayInputStream("not an image".toByteArray()))
        assertTrue(result is AvatarSaveResult.NotAnImage)
    }

    @Test
    fun `rejects a stream larger than the cap`() {
        val dir = createTempDir("avatar")
        val huge = ByteArrayInputStream(ByteArray(ProfileIdentity.AVATAR_MAX_BYTES.toInt() + 100))
        val result = ProfileIdentity.saveAvatar(dir, huge)
        assertTrue(result is AvatarSaveResult.TooLarge)
    }
}
