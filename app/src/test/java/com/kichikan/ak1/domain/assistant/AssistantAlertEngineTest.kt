package com.kichikan.ak1.domain.assistant
import com.kichikan.ak1.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
class AssistantAlertEngineTest{
 private val day=86_400_000L;private val zone=ZoneId.of("UTC")
 private fun member()=Member("m1","r1","آزمون",registeredAtEpochMillis=day)
 private fun input(m:Member,history:List<HistoryEvent> = emptyList(),sessions:List<Session> = emptyList(),requests:List<MissionRequest> = emptyList(),alerts:List<AssistantAlert> = emptyList())=AssistantAlertEngine.Input(listOf(m),history,sessions,emptyList(),requests,emptyList(),alerts)
 @Test fun lockSuppressesFirstThirtyDays(){assertTrue(AssistantAlertEngine.evaluate(input(member()),30*day,zone).isEmpty())}
 @Test fun inactivityFiresAfterLockAndThreshold(){assertTrue(AssistantAlertEngine.evaluate(input(member()),46*day,zone).any{it.ruleKey=="activity-14d"})}
 @Test fun pendingMissionRequestHasTwentyFourHourRule(){val now=32*day;val r=MissionRequest("req","mission","m1",requestedAt=now-day-1);assertTrue(AssistantAlertEngine.evaluate(input(member(),requests=listOf(r)),now,zone).any{it.ruleKey=="mission-request-24h-req"})}
 @Test fun doneFingerprintDoesNotReopen(){val now=46*day;val first=AssistantAlertEngine.evaluate(input(member()),now,zone);val done=first.map{it.copy(status=AssistantRecordStatus.DONE,completedAtEpochMillis=now)};val next=AssistantAlertEngine.evaluate(input(member(),alerts=done),now+day,zone);assertFalse(next.any{it.ruleKey=="activity-14d"&&it.status==AssistantRecordStatus.OPEN})}
 @Test fun attendanceIsNotAnIndividualSession(){val now=140*day;val event=HistoryEvent("h","m1",HistoryType.ATTENDANCE,title="حضور",createdAtEpochMillis=now-day);val result=AssistantAlertEngine.evaluate(input(member(),history=listOf(event)),now,zone);assertTrue(result.any{it.ruleKey=="session-3-calendar-months"})}
}