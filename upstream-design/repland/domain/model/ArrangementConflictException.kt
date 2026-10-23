package com.swan1127.repland.domain.model

/** A safe, actionable message for arrangements rejected by current hard constraints. */
class ArrangementConflictException : IllegalStateException(USER_MESSAGE) {
    val userMessage: String = USER_MESSAGE

    companion object {
        const val USER_MESSAGE = "安排与已有课程、休息或锁定时段冲突，请调整草案后重试。"
    }
}
