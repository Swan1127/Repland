package com.swan1127.repland.domain.model

import java.time.LocalDate

/** A single user-requested operation. Consent is bound to a freshly read planning revision. */
sealed interface ManualPlanChange {
    data class Place(val taskId: String, val date: LocalDate, val start: Int, val end: Int, val track: String) : ManualPlanChange
    data class Move(val segmentId: String, val start: Int, val end: Int, val track: String) : ManualPlanChange
    data class Remove(val segmentId: String) : ManualPlanChange
    data object Clear : ManualPlanChange
}

class PlanChangeConfirmationRequired(val revision: String, message: String) : IllegalArgumentException(message)

data class PendingManualPlanChange(val change: ManualPlanChange, val revision: String, val message: String)
