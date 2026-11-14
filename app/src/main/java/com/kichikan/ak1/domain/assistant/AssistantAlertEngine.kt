package com.kichikan.ak1.domain.assistant
import com.kichikan.ak1.domain.model.*
import java.time.Instant
import java.time.ZoneId
/** Deterministic offline rules; attendance is never treated as a one-to-one session. */
object AssistantAlertEngine {
 private const val DAY=86_400_000L
 data class Input(val members:List<Member>,val history:List<HistoryEvent>,val sessions:List<Session>,val missionCompletions:List<MissionCompletion>,val missionRequests:List<MissionRequest>,val followUps:List<AssistantFollowUp>,val existingAlerts:List<AssistantAlert>,val growthLastAction:Map<String,Map<String,Long>> = emptyMap())
 fun evaluate(input:Input,now:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault()):List<AssistantAlert>{
  val result=input.existingAlerts.toMutableList()
  input.members.forEach { m ->
   val start=m.monitoringStartAtEpochMillis ?: m.registeredAtEpochMillis.takeIf{it>0L} ?: now
   if(now<start+30L*DAY)return@forEach
   val events=input.history.filter{it.memberId==m.id}; val candidates=mutableListOf<AssistantAlert>()
   fun base(last:Long?)=maxOf(last?:0L,start)
   fun add(tab:AssistantAlertTab,rule:String,title:String,msg:String,at:Long,ids:List<String> = emptyList(),extra:String=""){
    val fp="$rule:$at:$extra"
    candidates+=AssistantAlert("assistant:"+m.id+":"+rule+":"+fp.hashCode(),m.id,tab,rule,title,msg,1,createdAtEpochMillis=now,sourceRecordIds=ids,conditionFingerprint=fp,lastEvaluatedAtEpochMillis=now)
   }
   val lastActivity=events.maxOfOrNull{it.createdAtEpochMillis}
   if(now-base(lastActivity)>=14L*DAY)add(AssistantAlertTab.MEMBER_ACTIVITY,"activity-14d","۱۴ روز بدون فعالیت ثبت‌شده","از آخرین رویداد ثبت‌شده این عضو ۱۴ روز یا بیشتر گذشته است.",base(lastActivity),events.maxByOrNull{it.createdAtEpochMillis}?.let{listOf(it.id)}?:emptyList())
   val recentStart=now-14L*DAY;val prevStart=recentStart-14L*DAY
   val prev=events.filter{it.type==HistoryType.XP_EARNED&&it.createdAtEpochMillis in prevStart until recentStart}
   val recent=events.filter{it.type==HistoryType.XP_EARNED&&it.createdAtEpochMillis in recentStart..now}
   val oldXp=prev.sumOf{it.amount?:0};val newXp=recent.sumOf{it.amount?:0}
   if(oldXp>=10&&newXp*2<oldXp&&start<=prevStart){val ids=(prev+recent).map{it.id}.sorted();add(AssistantAlertTab.XP_TREND,"xp-drop-14d","کاهش روند XP","XP این عضو در بازه ۱۴روزه اخیر کمتر از نصف بازه ۱۴روزه پیش از آن است.",prev.maxOfOrNull{it.createdAtEpochMillis}?:prevStart,ids,ids.joinToString(","))}
   val lastMission=maxOf(input.missionCompletions.filter{m.id in it.memberIds}.maxOfOrNull{it.completedAt}?:0L,events.filter{it.type==HistoryType.MISSION_COMPLETED}.maxOfOrNull{it.createdAtEpochMillis}?:0L).takeIf{it>0L}
   if(now-base(lastMission)>=30L*DAY)add(AssistantAlertTab.MISSION,"mission-30d","۳۰ روز بدون ثبت مأموریت","از آخرین تکمیل مأموریت ثبت‌شده این عضو ۳۰ روز یا بیشتر گذشته است.",base(lastMission))
   input.missionRequests.filter{it.memberId==m.id&&it.status==MissionRequestStatus.PENDING&&now-it.requestedAt>=DAY}.forEach{r->add(AssistantAlertTab.MISSION,"mission-request-24h-"+r.id,"درخواست مأموریت در انتظار بررسی","درخواست مأموریت بیش از ۲۴ ساعت است که بررسی نشده است.",r.requestedAt,listOf(r.id),r.id)}
   val growth=input.growthLastAction[m.id].orEmpty()
   listOf("توانمندی","خودسازی").forEach{branch->val at=growth[branch];if(now-base(at)>=30L*DAY)add(AssistantAlertTab.GROWTH_MAP,"growth-30d-"+branch,"۳۰ روز بدون پیشرفت در "+branch,"در شاخه "+branch+" از نقشه کمال، ۳۰ روز یا بیشتر مورد فعالی ثبت نشده است.",base(at))}
   val lastSession=input.sessions.filter{it.memberId==m.id}.maxOfOrNull{it.startsAt}
   val cutoff=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli()
   if((lastSession?:start)<=cutoff)add(AssistantAlertTab.SESSIONS,"session-3-calendar-months","سه ماه بدون جلسه فردی","از آخرین جلسه فردی ثبت‌شده سه ماه تقویمی یا بیشتر گذشته است.",lastSession?:start)
   val lastReading=m.readingBooks.maxOfOrNull{it.completedAtEpochMillis}
   if(now-base(lastReading)>=30L*DAY)add(AssistantAlertTab.READING,"reading-30d","۳۰ روز بدون ثبت کتاب تکمیل‌شده","در ۳۰ روز اخیر رکورد تازه‌ای از کتاب تکمیل‌شده ثبت نشده است؛ این به‌معنای مطالعه‌نکردن نیست.",base(lastReading))
   input.followUps.filter{it.memberId==m.id&&it.status==AssistantRecordStatus.OPEN&&it.dueAtEpochMillis<=now}.forEach{f->add(AssistantAlertTab.FOLLOW_UP,"follow-up-"+f.id,"پیگیری سررسیدشده","زمان پیگیری «"+f.title+"» فرارسیده است.",f.dueAtEpochMillis,listOf(f.id),f.id)}
   candidates.forEach{fresh->val seen=result.any{it.memberId==m.id&&it.conditionFingerprint==fresh.conditionFingerprint};val open=result.indexOfLast{it.memberId==m.id&&it.ruleKey==fresh.ruleKey&&it.status==AssistantRecordStatus.OPEN};if(!seen&&open<0)result+=fresh else if(open>=0)result[open]=result[open].copy(lastEvaluatedAtEpochMillis=now)}
  }
  return result
 }
}