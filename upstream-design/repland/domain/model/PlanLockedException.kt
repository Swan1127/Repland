package com.swan1127.repland.domain.model

/** A locked placement must be explicitly unlocked before it can be changed. */
class PlanLockedException : IllegalStateException(USER_MESSAGE) {
    val userMessage: String = USER_MESSAGE

    companion object {
        const val USER_MESSAGE = "该时段已锁定，请先解锁后再修改。"
    }
}
