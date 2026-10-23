package com.swan1127.repland.domain.model

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * A locally-stored, purely cosmetic identity: what the user wants to be called and shown as.
 *
 * Split on purpose. Everything here is pure and takes no `Context`, no `Uri`, no Android type,
 * because the questions worth answering — "is this nickname legal", "what two characters do I
 * draw in the empty avatar", "is this byte buffer an image" — are exactly the ones a screenshot
 * cannot answer. The Android half (opening a picked `Uri`, writing a file) lives in [ProfileStore]
 * and is allowed to be thin, because the decisions already live here.
 */
data class UserIdentity(
    /** Already sanitized by [ProfileIdentity.sanitizeNickname]; empty string means "not set". */
    val nickname: String,
    /** Absolute path of the avatar inside app-private storage, or null when none is set. */
    val avatarPath: String?,
) {
    companion object {
        val EMPTY = UserIdentity(nickname = "", avatarPath = null)
    }
}

/** The image formats the app will keep as an avatar, by their file signatures. */
enum class ImageFormat(val extension: String) {
    JPEG("jpg"),
    PNG("png"),
    GIF("gif"),
    WEBP("webp"),
}

/** Outcome of handing a picked file to the store. */
sealed interface AvatarSaveResult {
    /** Accepted and written; [path] is where it lives now. */
    data class Saved(val path: String) : AvatarSaveResult

    /** The bytes are not a decodable image. */
    data object NotAnImage : AvatarSaveResult

    /** The stream ran past the size cap before an image signature could be confirmed. */
    data object TooLarge : AvatarSaveResult
}

object ProfileIdentity {

    /** Upper bound on a stored nickname, in characters (not bytes). */
    const val NICKNAME_MAX_CHARS = 20

    /** Upper bound on an imported avatar, in bytes. 5 MB keeps decode cheap and the footprint small. */
    const val AVATAR_MAX_BYTES = 5L * 1024 * 1024

    /**
     * Normalises a raw nickname the user typed.
     *
     * - invisible/control characters (including newlines and zero-width) are dropped, never kept —
     *   a nickname that spanned two lines would otherwise break the centred header layout;
     * - internal runs of whitespace collapse to a single space, so a copy-paste " 小 茂 " becomes
     *   "小 茂";
     * - the result is trimmed and then capped at [NICKNAME_MAX_CHARS].
     *
     * Returns "" when nothing legible is left, which is the same state as "never set".
     */
    fun sanitizeNickname(raw: String): String =
        raw.replace(Regex("[\\p{Cc}\\p{Cf}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(NICKNAME_MAX_CHARS)

    /**
     * What to display when the user has not chosen a nickname.
     *
     * [fallback] is the locale string for "you" (e.g. 学习者 / Learner). When both the nickname
     * and the fallback are blank, returns "" — the UI then hides the name line rather than drawing
     * an empty pill.
     */
    fun displayName(nickname: String, fallback: String): String {
        val chosen = nickname.trim()
        if (chosen.isNotEmpty()) return chosen
        return fallback.trim()
    }

    /**
     * One or two characters to draw in the empty-avatar circle.
     *
     * - A name with two or more whitespace-separated words (a Latin "Alice Chen") yields the first
     *   letter of the first and last word: "AC".
     * - A single token is taken whole up to two characters. A pure-ASCII token ("Alice") gets one
     *   letter ("A"); anything with non-ASCII script — Chinese, Japanese, Korean — gets two
     *   ("小茂"), matching the convention of showing two characters of a CJK display name.
     */
    fun initials(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return ""
        val tokens = clean.split(' ')
        if (tokens.size >= 2) {
            val first = tokens.first().firstOrNull()?.toString().orEmpty()
            val last = tokens.last().firstOrNull()?.toString().orEmpty()
            return (first + last).ifEmpty { clean.take(2) }
        }
        val single = tokens.first()
        return if (single.all { it.isAsciiLetter() }) single.take(1) else single.take(2)
    }

    /**
     * Identifies an image from its leading bytes.
     *
     * A `.jpg` whose real contents are text is exactly the kind of thing a MIME-type-only check
     * accepts and the decoder then fails on. Reading the signature here is the cheap, pure half of
     * "is this actually a picture"; the store refuses anything this returns null for.
     */
    fun sniffImageFormat(header: ByteArray): ImageFormat? {
        // Each branch guards its own minimum length: JPEG/PNG/GIF need only a few bytes, but WEBP
        // needs twelve (the "RIFF....WEBP" layout). Requiring a blanket 12 would wrongly reject a
        // short-but-valid PNG, so the check is per-format.
        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
        ) return ImageFormat.JPEG
        if (header.size >= 4 &&
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
        ) return ImageFormat.PNG
        if (header.size >= 3 &&
            header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte()
        ) return ImageFormat.GIF
        if (header.size >= 12 &&
            header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
            header[8] == 0x57.toByte() && header[9] == 0x45.toByte() &&
            header[10] == 0x42.toByte() && header[11] == 0x50.toByte()
        ) return ImageFormat.WEBP
        return null
    }

    /**
     * Whether a picked file should be rejected before its bytes are even read.
     *
     * [sizeBytes] is what the picker reported; a negative value means "not reported", in which
     * case we do not reject on size — the size cap is enforced again while the bytes are copied, so
     * a huge file with an unknown size is caught there, not here. [mimeType] null is likewise left
     * to the byte sniff, because some providers do not report a MIME type at all.
     */
    fun avatarRejection(sizeBytes: Long, mimeType: String?): AvatarRejection? = when {
        mimeType != null && !mimeType.startsWith("image/", ignoreCase = true) -> AvatarRejection.NOT_AN_IMAGE
        sizeBytes >= 0 && sizeBytes > AVATAR_MAX_BYTES -> AvatarRejection.TOO_LARGE
        else -> null
    }

    /** Copies [source] to a fresh avatar file, sniffing the format and enforcing the size cap. */
    fun copyAvatar(dir: File, source: InputStream): AvatarSaveResult {
        val buffer = ByteArrayOutputStream()
        var total = 0L
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = source.read(chunk)
            if (n <= 0) break
            total += n
            if (total > AVATAR_MAX_BYTES) {
                buffer.reset()
                return AvatarSaveResult.TooLarge
            }
            buffer.write(chunk, 0, n)
        }
        val bytes = buffer.toByteArray()
        val format = sniffImageFormat(bytes) ?: return AvatarSaveResult.NotAnImage
        val file = File(dir, "avatar.${format.extension}")
        dir.mkdirs()
        file.writeBytes(bytes)
        return AvatarSaveResult.Saved(file.absolutePath)
    }

    /** Reads a whole [InputStream] and copies it as an avatar, closing the stream. */
    fun saveAvatar(dir: File, source: InputStream): AvatarSaveResult =
        source.use { copyAvatar(dir, it) }
}

/** Why a picked file was declined. */
enum class AvatarRejection {
    /** The picker said it was not an image type. */
    NOT_AN_IMAGE,

    /** The picker reported a size above the cap. */
    TOO_LARGE,
}

private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'
