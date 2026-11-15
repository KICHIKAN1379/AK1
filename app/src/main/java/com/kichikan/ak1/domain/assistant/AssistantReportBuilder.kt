package com.kichikan.ak1.domain.assistant
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.service.RingReport
import java.time.LocalDate
import java.time.ZoneId
/** Member-scoped deterministic report data. Private notes and inferred traits are never read. */
object AssistantReportBuilder {
 enum class PeriodKind { MONTHLY, YEARLY }
 data class Period(val kind:PeriodKind,val year:Int,val month:Int=1)
 data class Metric(val id:String,val label:String,val value:Double,val previous:Double,val unit:String="",val isPercent:Boolean=false)
 data class Bucket(val label:String,val xp:Double,val points:Double,val diamonds:Double,val missions:Int,val growth:Int,val readingPages:Int,val levelChanges:Int)
 data class Result(val memberName:String,val period:Period,val title:String,val startEpochMillis:Long,val endEpochMillis:Long,val metrics:List<Metric>,val series:List<Bucket>,val currentLevel:Int,val completedBooksTotal:Int,val completedBooksInPeriod:Int,val dataNotes:List<String>)
 fun build(member:Member,history:List<HistoryEvent>,completions:List<MissionCompletion>,period:Period,growthUnlockEpochDays:List<Long>,growthItemsTotal:Int):Result {
  require(period.year>0);require(period.month in 1..12)
  val kind=if(period.kind==PeriodKind.MONTHLY)RingReport.Kind.MONTHLY else RingReport.Kind.YEARLY
  val cur=RingReport.range(kind,period.year,period.month);val prev=RingReport.previousRange(kind,period.year,period.month)
  val events=history.filter{it.memberId==member.id};val books=member.readingBooks;val zone=ZoneId.systemDefault()
  fun inside(t:Long,r:RingReport.Range)=t>=r.start&&t<r.end
  fun sum(r:RingReport.Range,type:HistoryType)=events.filter{it.type==type&&inside(it.createdAtEpochMillis,r)}.sumOf{(it.amount?:0).toDouble()}
  fun net(r:RingReport.Range,e:HistoryType,d:HistoryType,s:HistoryType?=null)=sum(r,e)-sum(r,d)-(s?.let{sum(r,it)}?:0.0)
  fun missions(r:RingReport.Range)=completions.count{member.id in it.memberIds&&inside(it.completedAt,r)}
  fun growth(r:RingReport.Range)=growthUnlockEpochDays.count{day->inside(LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli(),r)}
  fun pages(r:RingReport.Range)=books.filter{inside(it.completedAtEpochMillis,r)}.sumOf{it.pages}
  fun levels(r:RingReport.Range)=events.count{it.type==HistoryType.LEVEL_CHANGED&&inside(it.createdAtEpochMillis,r)}
  val xp=net(cur,HistoryType.XP_EARNED,HistoryType.XP_DECREASED);val xpPrev=net(prev,HistoryType.XP_EARNED,HistoryType.XP_DECREASED)
  val points=net(cur,HistoryType.POINTS_EARNED,HistoryType.POINTS_DECREASED,HistoryType.POINTS_SPENT);val pointsPrev=net(prev,HistoryType.POINTS_EARNED,HistoryType.POINTS_DECREASED,HistoryType.POINTS_SPENT)
  val diamonds=net(cur,HistoryType.DIAMONDS_EARNED,HistoryType.DIAMONDS_DECREASED,HistoryType.DIAMONDS_SPENT);val diamondsPrev=net(prev,HistoryType.DIAMONDS_EARNED,HistoryType.DIAMONDS_DECREASED,HistoryType.DIAMONDS_SPENT)
  val g=growth(cur);val gp=growth(prev);val total=growthItemsTotal.coerceAtLeast(0)
  val metrics=listOf(Metric("xp","XP خالص",xp,xpPrev,"XP"),Metric("points","امتیاز خالص",points,pointsPrev,"امتیاز"),
   Metric("diamonds","الماس خالص",diamonds,diamondsPrev,"الماس"),Metric("missions","مأموریت تکمیل‌شده",missions(cur).toDouble(),missions(prev).toDouble(),"مورد"),
   Metric("growth","پیشرفت نقشه کمال",if(total==0)0.0 else g*100.0/total,if(total==0)0.0 else gp*100.0/total,"٪",true),
   Metric("reading","صفحات کتاب تکمیل‌شده",pages(cur).toDouble(),pages(prev).toDouble(),"صفحه"),
   Metric("levels","تغییر سطح ثبت‌شده",levels(cur).toDouble(),levels(prev).toDouble(),"بار"),
   Metric("books","کتاب تکمیل‌شده",books.count{inside(it.completedAtEpochMillis,cur)}.toDouble(),books.count{inside(it.completedAtEpochMillis,prev)}.toDouble(),"کتاب"))
  val ranges=if(period.kind==PeriodKind.YEARLY)(1..12).map{m->JalaliCalendar.MONTH_NAMES[m-1] to RingReport.range(RingReport.Kind.MONTHLY,period.year,m)}
   else(0 until 5).mapNotNull{w->val s=cur.start+w*7L*86_400_000L;val e=minOf(cur.end,s+7L*86_400_000L);if(s<e)"هفته "+(w+1) to RingReport.Range(s,e)else null}
  val series=ranges.map{(label,r)->Bucket(label,net(r,HistoryType.XP_EARNED,HistoryType.XP_DECREASED),net(r,HistoryType.POINTS_EARNED,HistoryType.POINTS_DECREASED,HistoryType.POINTS_SPENT),net(r,HistoryType.DIAMONDS_EARNED,HistoryType.DIAMONDS_DECREASED,HistoryType.DIAMONDS_SPENT),missions(r),growth(r),pages(r),levels(r))}
  val title=if(period.kind==PeriodKind.MONTHLY)"کارنامه "+JalaliCalendar.MONTH_NAMES[period.month-1]+" "+period.year else "کارنامه سال "+period.year
  val notes=buildList{if(events.isEmpty())add("رویداد مالی یا فعالیتی در پرونده ثبت نشده است.");if(total==0)add("فهرست فعالی برای نقشه کمال در دسترس نیست.");if(books.isEmpty())add("کتاب تکمیل‌شده‌ای ثبت نشده؛ این گزارش درباره مطالعه‌نکردن نتیجه‌گیری نمی‌کند.");if(completions.none{member.id in it.memberIds})add("رکورد تکمیل مأموریت برای این عضو موجود نیست.")}
  return Result(member.name,period,title,cur.start,cur.end,metrics,series,member.economy.level,books.size,books.count{inside(it.completedAtEpochMillis,cur)},notes)
 }
}
