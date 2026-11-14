package com.kichikan.ak1.domain.assistant

enum class AssistantFollowUpSource { MANUAL, BIRTHDAY, SYSTEM }

data class AssistantFollowUp(
    val id: String, val memberId: String, val title: String, val description: String = "",
    val dueAtEpochMillis: Long, val status: AssistantRecordStatus = AssistantRecordStatus.OPEN,
    val createdAtEpochMillis: Long, val completedAtEpochMillis: Long? = null,
    val sourceType: AssistantFollowUpSource = AssistantFollowUpSource.MANUAL,
    val sourceReference: String? = null
)
