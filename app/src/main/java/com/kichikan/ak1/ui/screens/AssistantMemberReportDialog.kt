package com.kichikan.ak1.ui.screens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.data.AssistantDataState
import com.kichikan.ak1.domain.assistant.AssistantReportBuilder
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.Member
import com.kichikan.ak1.ui.GrowthStore
import com.kichikan.ak1.ui.MemberReportJpegExporter
import com.kichikan.ak1.ui.theme.Card as AkCard
import com.kichikan.ak1.ui.theme.Neon
import java.time.LocalDate
@Composable
internal fun AssistantMemberReportDialog(member:Member,state:AssistantDataState,onDismiss:()->Unit){
 val context=LocalContext.current;val prefs=remember{context.getSharedPreferences("ak1_settings",android.content.Context.MODE_PRIVATE)}
 val today=JalaliCalendar.fromGregorian(LocalDate.now());var kind by remember{mutableStateOf(AssistantReportBuilder.PeriodKind.MONTHLY)}
 var year by remember{mutableIntStateOf(today.year)};var month by remember{mutableIntStateOf(today.month)}
 var chart by remember{mutableStateOf("xp")};var bytes by remember{mutableStateOf<ByteArray?>(null)};var message by remember{mutableStateOf<String?>(null)}
 val saver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")){uri->val data=bytes
  if(uri!=null&&data!=null)message=runCatching{val stream=context.contentResolver.openOutputStream(uri)?:error("مسیر ذخیره در دسترس نیست");stream.use{it.write(data)};"تصویر کارنامه ذخیره شد."}.getOrElse{"ذخیره تصویر انجام نشد: "+(it.message?:"خطای ناشناخته")}}
 val active=remember(prefs){GrowthStore.knowledge(prefs).filter{GrowthStore.isActive(prefs,"knowledge",it)}+GrowthStore.skills(prefs).filter{GrowthStore.isActive(prefs,"skills",it)}+GrowthStore.characters(prefs).filter{GrowthStore.isActive(prefs,"characters",it)}}
 val unlocked=GrowthStore.unlocked(prefs,member.id);val days=active.mapNotNull{unlocked[it]}
 val report=remember(member,state.history,state.completions,kind,year,month,days,active.size){AssistantReportBuilder.build(member,state.history,state.completions,AssistantReportBuilder.Period(kind,year,month),days,active.size)}
 val charts=listOf("xp" to ("XP" to Neon.Cyan),"points" to ("امتیاز" to Neon.Violet),"diamonds" to ("الماس" to Neon.Magenta),"missions" to ("مأموریت" to Neon.Gold),"growth" to ("نقشه کمال" to Neon.Cyan),"reading" to ("کتاب‌خوانی" to Neon.Violet),"levels" to ("تغییر سطح" to Neon.Magenta))
 val selected=charts.firstOrNull{it.first==chart}?:charts.first()
 val values=report.series.map{b->when(chart){"points"->b.points;"diamonds"->b.diamonds;"missions"->b.missions.toDouble();"growth"->b.growth.toDouble();"reading"->b.readingPages.toDouble();"levels"->b.levelChanges.toDouble();else->b.xp}}
 AlertDialog(onDismissRequest=onDismiss,title={Text("کارنامه "+member.name)},text={
  Column(Modifier.fillMaxWidth().heightIn(max=620.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
   Text("بازه گزارش",style=MaterialTheme.typography.titleSmall)
   Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
    FilterChip(kind==AssistantReportBuilder.PeriodKind.MONTHLY,{kind=AssistantReportBuilder.PeriodKind.MONTHLY},label={Text("ماهانه")})
    FilterChip(kind==AssistantReportBuilder.PeriodKind.YEARLY,{kind=AssistantReportBuilder.PeriodKind.YEARLY},label={Text("سالانه")})}
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){(today.year-3..today.year).forEach{y->FilterChip(year==y,{year=y},label={Text(y.toString())})}}
   if(kind==AssistantReportBuilder.PeriodKind.MONTHLY)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){(1..12).forEach{m->FilterChip(month==m,{month=m},label={Text(JalaliCalendar.MONTH_NAMES[m-1])})}}
   Text(report.title,style=MaterialTheme.typography.titleMedium,color=Neon.Cyan)
   Text("سطح فعلی: "+report.currentLevel+" • کتاب تکمیل‌شده در کل: "+report.completedBooksTotal,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   report.metrics.forEach{m->AkCard(Modifier.fillMaxWidth(),accent=when(m.id){"xp"->Neon.Cyan;"points"->Neon.Violet;"diamonds"->Neon.Magenta;"missions"->Neon.Gold;"growth"->Neon.Green;"reading"->Neon.Sky;else->Neon.Orange}){
    Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(m.label,style=MaterialTheme.typography.labelMedium);Text("این دوره: "+m.value+" "+m.unit+" • دوره قبل: "+m.previous,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   }}
   Text("نمودار روند",style=MaterialTheme.typography.titleMedium)
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){charts.forEach{(id,pair)->FilterChip(chart==id,{chart=id},label={Text(pair.first)})}}
   AkCard(Modifier.fillMaxWidth(),accent=selected.second.second){Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
    Text(selected.second.first,style=MaterialTheme.typography.titleSmall)
    Canvas(Modifier.fillMaxWidth().height(145.dp)){val max=values.maxOfOrNull{kotlin.math.abs(it)}?.coerceAtLeast(1.0)?:1.0;val baseline=size.height*.78f
     drawLine(Color.White.copy(alpha=.25f),Offset(0f,baseline),Offset(size.width,baseline),1.dp.toPx());val step=size.width/values.size.coerceAtLeast(1)
     values.forEachIndexed{i,v->val bh=(kotlin.math.abs(v)/max*size.height*.58).toFloat();val left=step*i+step*.2f;val right=step*(i+1)-step*.2f
      drawRoundRect(selected.second.second,Offset(left,if(v>=0)baseline-bh else baseline),androidx.compose.ui.geometry.Size((right-left).coerceAtLeast(1f),bh),cornerRadius=androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()))}}
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.SpaceEvenly){report.series.forEach{Text(it.label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
   }}
   report.dataNotes.forEach{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   Button(onClick={bytes=MemberReportJpegExporter.render(report);saver.launch("karname-"+member.id+"-"+year+"-"+month+".jpg")},modifier=Modifier.fillMaxWidth()){Text("ذخیره کارنامه JPEG")}
   message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
  }
 },confirmButton={TextButton(onClick=onDismiss){Text("بستن")}})
}
