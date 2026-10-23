package com.swan1127.repland.data.importer

import android.content.Context
import android.net.Uri
import com.swan1127.repland.domain.ports.TimetableSource

/**
 * Binds a picked `content://` id to a [TimetableSource].
 *
 * This is the single place in the app where a Uri meets a `ContentResolver`. Keeping the
 * resolution here — rather than inside the ViewModel — is exactly what lets the import logic be
 * exercised without a device: the ViewModel sees a function that returns a stream, and a test
 * hands it one over an in-memory document.
 *
 * The lambda is deliberately lazy. It runs when the importer opens the file, not when the user
 * taps the picker, so a cancelled or revoked grant surfaces as the importer's own "无法读取所选文件"
 * rather than as a crash on the UI thread.
 */
fun Context.timetableSource(uri: Uri): TimetableSource =
    TimetableSource { contentResolver.openInputStream(uri) }
