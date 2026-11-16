package com.kichikan.ak1.domain.assistant

enum class AssistantAlertTab { MEMBER_ACTIVITY, XP_TREND, MISSION, GROWTH_MAP, SESSIONS, READING, FOLLOW_UP }
enum class AssistantRecordStatus { OPEN, DONE }

data class AssistantAlert(
    val id: String, val memberId: String, val tab: AssistantAlertTab, val ruleKey: String,
    val title: String, val message: String, val severity: Int,
    val status: AssistantRecordStatus = AssistantRecordStatus.OPEN,
    val createdAtEpochMillis: Long, val completedAtEpochMillis: Long? = null,
    val sourceRecordIds: List<String> = emptyList(), val conditionFingerprint: String,
    val lastEvaluatedAtEpochMillis: Long = createdAtEpochMillis
)
