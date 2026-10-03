package com.swan1127.repland.domain.model

/** Small offline query grammar. Unknown language still goes to the optional interpreter, not a write command. */
object ArrangementReadIntent {
    fun queryScope(text: String): TaskQueryScope? {
        val words = text.trim().lowercase()
        val query = Regex("^(?:请|帮我)?(?:查询|查看|列出)").containsMatchIn(words) ||
            Regex("^(?:please\\s+)?(?:show|list|what\\s+(?:are|is))\\b").containsMatchIn(words)
        if (!query || !(words.contains("任务") || Regex("\\btasks?\\b").containsMatchIn(words))) return null
        return when {
            words.contains("逾期") || words.contains("overdue") -> TaskQueryScope.OVERDUE
            words.contains("待安排") || words.contains("inbox") || words.contains("unscheduled") -> TaskQueryScope.INBOX
            words.contains("今天") || words.contains("today") -> TaskQueryScope.TODAY
            else -> TaskQueryScope.ALL_ACTIVE
        }
    }
}
