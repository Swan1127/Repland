package com.swan1127.repland.data.json

/**
 * A small, strict JSON reader that ships in the app rather than only in the tests.
 *
 * `org.json` is part of the Android framework, which means it is a **stub** in a JVM unit
 * test — every call throws `RuntimeException("Stub!")`. Parsing provider output is the part
 * of the AI adapter most likely to be wrong (models wrap JSON in prose, omit optional
 * fields, and occasionally emit a bare number), so it has to be testable on a plain JVM.
 * Using our own reader means the code under test is the code that runs on the device.
 *
 * Deliberately strict: malformed input throws rather than silently yielding a partial
 * object, so a caller cannot mistake "the model sent garbage" for "the model sent nothing".
 *
 * Returns `Map<String, Any?>` / `List<Any?>` / `String` / `Double` / `Boolean` / `null`.
 */
object StrictJson {

    fun parse(text: String): Any? {
        val cursor = Cursor(text)
        val value = cursor.readValue()
        cursor.skipWhitespace()
        check(cursor.atEnd()) { "trailing content at offset ${cursor.offset}" }
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> = parse(text) as Map<String, Any?>

    private class Cursor(private val text: String) {
        var offset = 0
            private set

        fun atEnd(): Boolean = offset >= text.length

        fun skipWhitespace() {
            while (offset < text.length && text[offset].isWhitespace()) offset++
        }

        fun readValue(): Any? {
            skipWhitespace()
            check(!atEnd()) { "unexpected end of input" }
            return when (val c = text[offset]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else fail("unexpected '$c'")
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                offset++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                result[key] = readValue()
                skipWhitespace()
                when (val c = peek()) {
                    ',' -> offset++
                    '}' -> {
                        offset++
                        return result
                    }

                    else -> fail("expected ',' or '}' but found '$c'")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                offset++
                return result
            }
            while (true) {
                result += readValue()
                skipWhitespace()
                when (val c = peek()) {
                    ',' -> offset++
                    ']' -> {
                        offset++
                        return result
                    }

                    else -> fail("expected ',' or ']' but found '$c'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                check(!atEnd()) { "unterminated string" }
                when (val c = text[offset++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        check(!atEnd()) { "unterminated escape" }
                        when (val e = text[offset++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                check(offset + 4 <= text.length) { "truncated \\u escape" }
                                sb.append(text.substring(offset, offset + 4).toInt(16).toChar())
                                offset += 4
                            }

                            else -> fail("bad escape '\\$e'")
                        }
                    }

                    else -> {
                        check(c.code >= 0x20) {
                            "raw control character U+%04x inside a JSON string".format(c.code)
                        }
                        sb.append(c)
                    }
                }
            }
        }

        private fun readNumber(): Double {
            val start = offset
            if (peek() == '-') offset++
            while (!atEnd() && (text[offset].isDigit() || text[offset] in ".eE+-")) offset++
            val raw = text.substring(start, offset)
            return raw.toDoubleOrNull() ?: fail("bad number '$raw'")
        }

        private fun literal(word: String, value: Any?): Any? {
            check(text.startsWith(word, offset)) { "expected '$word' at offset $offset" }
            offset += word.length
            return value
        }

        private fun peek(): Char {
            check(!atEnd()) { "unexpected end of input" }
            return text[offset]
        }

        private fun expect(c: Char) {
            check(peek() == c) { "expected '$c' but found '${peek()}'" }
            offset++
        }

        private fun fail(message: String): Nothing = error("$message at offset $offset")
    }
}
