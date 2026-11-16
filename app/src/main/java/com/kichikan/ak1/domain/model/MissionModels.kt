package com.kichikan.ak1.domain.model

enum class MissionType { INDIVIDUAL, GROUP }

data class Mission(
    val id: String,
    val title: String,
    val description: String = "",
    val type: MissionType = MissionType.INDIVIDUAL,
    val xpReward: Int = 0,
    val pointsReward: Int = 0,
    val diamondReward: Int = 0,
    val active: Boolean = true,
    val startAt: Long? = null,
    val endAt: Long? = null,
    /** Hidden missions: the mentor records them once per member and the date is shown on the member. */
    val hidden: Boolean = false
)

data class MissionCompletion(
    val id: String,
    val missionId: String,
    val memberIds: List<String>,
    val completedAt: Long,
    val reason: String? = null
)


enum class MissionRequestStatus { PENDING, APPROVED, REJECTED }

data class MissionRequest(
    val id: String, val missionId: String, val memberId: String,
    val message: String = "", val requestedAt: Long,
    val status: MissionRequestStatus = MissionRequestStatus.PENDING,
    val reviewedAt: Long? = null, val mentorNote: String? = null
)

data class MissionNotice(
    val id: String, val title: String, val message: String, val createdAt: Long,
    val requestId: String? = null, val read: Boolean = false
)
