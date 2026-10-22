package com.swan1127.repland.testkit

/**
 * The reader used to be defined here. It now ships in the app as
 * `com.swan1127.repland.data.json.StrictJson`, because the AI adapter parses provider output
 * with it and that parsing has to be testable on a plain JVM (`org.json` is a stub there).
 * Keeping the old name as an alias means the exporter tests did not have to move.
 */
typealias MiniJson = com.swan1127.repland.data.json.StrictJson

/*
 * The original implementation lived here and was moved verbatim to
 * `repland/data/json/StrictJson.kt`. See that file for the grammar notes.
 */

