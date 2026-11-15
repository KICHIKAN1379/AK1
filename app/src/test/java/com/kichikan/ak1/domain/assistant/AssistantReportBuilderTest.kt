package com.kichikan.ak1.domain.assistant
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.service.RingReport
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class AssistantReportBuilderTest {
 @Test fun reportScopesLedgerToSelectedMemberAndPeriod(){
  val j=JalaliCalendar.fromGregorian(LocalDate.of(2026,10,15));val p=AssistantReportBuilder.Period(AssistantReportBuilder.PeriodKind.MONTHLY,j.year,j.month)
  val r=RingReport.range(RingReport.Kind.MONTHLY,j.year,j.month);val m=Member("m1","r","عضو")
  val own=HistoryEvent("h1","m1",HistoryType.XP_EARNED,40,"XP",createdAtEpochMillis=r.start+1000)
  val other=HistoryEvent("h2","m2",HistoryType.XP_EARNED,900,"XP",createdAtEpochMillis=r.start+2000)
  val out=AssistantReportBuilder.build(m,listOf(own,other),emptyList(),p,emptyList(),0)
  assertEquals(40.0,out.metrics.first{it.id=="xp"}.value,0.0);assertTrue(out.series.isNotEmpty())
 }
 @Test fun reportIncludesBooksMissionsAndGrowth(){
  val j=JalaliCalendar.fromGregorian(LocalDate.of(2026,10,15));val p=AssistantReportBuilder.Period(AssistantReportBuilder.PeriodKind.MONTHLY,j.year,j.month)
  val r=RingReport.range(RingReport.Kind.MONTHLY,j.year,j.month);val book=ReadingBook("b1","کتاب",10,120,2,r.start+3000)
  val m=Member("m1","r","عضو",readingBooks=listOf(book));val completion=MissionCompletion("c1","mission",listOf("m1"),r.start+4000)
  val day=Instant.ofEpochMilli(r.start+5000).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
  val out=AssistantReportBuilder.build(m,emptyList(),listOf(completion),p,listOf(day),10)
  assertEquals(120.0,out.metrics.first{it.id=="reading"}.value,0.0);assertEquals(1.0,out.metrics.first{it.id=="missions"}.value,0.0);assertEquals(1,out.series.sumOf{it.growth})
 }
}
