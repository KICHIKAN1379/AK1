package com.kichikan.ak1.ui
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.kichikan.ak1.domain.assistant.AssistantReportBuilder
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import java.time.Instant
import java.time.ZoneId
/** Persian/RTL JPEG renderer. StaticLayout preserves Arabic shaping and bidirectional text. */
object MemberReportJpegExporter {
 fun render(report:AssistantReportBuilder.Result):ByteArray {
  val w=1600;val h=2600;val bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val c=Canvas(bmp)
  c.drawRect(0f,0f,w.toFloat(),h.toFloat(),Paint(Paint.ANTI_ALIAS_FLAG).apply{shader=android.graphics.LinearGradient(0f,0f,w.toFloat(),h.toFloat(),intArrayOf(0xFF101B42.toInt(),0xFF21104B.toInt(),0xFF062E3A.toInt()),null,android.graphics.Shader.TileMode.CLAMP)})
  val white=0xFFF7F5FF.toInt();val muted=0xFFC7C4E3.toInt();val cyan=0xFF39E8F4.toInt();val violet=0xFF9D8CFF.toInt();val pink=0xFFFF78B7.toInt();val gold=0xFFFFD66B.toInt()
  fun text(s:String,x:Int,y:Int,width:Int,size:Float,color:Int,bold:Boolean=false,alignment:Layout.Alignment=Layout.Alignment.ALIGN_NORMAL):Int{
   if(s.isBlank())return 0
   val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size;typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)}
   val l=StaticLayout.Builder.obtain(s,0,s.length,p,width).setAlignment(alignment).setTextDirection(TextDirectionHeuristics.RTL).setIncludePad(false).setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).build()
   c.save();c.translate(x.toFloat(),y.toFloat());l.draw(c);c.restore();return l.height
  }
  fun card(x:Float,y:Float,width:Float,height:Float,accent:Int){c.drawRoundRect(RectF(x,y,x+width,y+height),28f,28f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0x24FFFFFF});c.drawRoundRect(RectF(x,y,x+width,y+7f),4f,4f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=accent})}
  fun n(v:Double)=if(v%1.0==0.0)v.toLong().toString()else"%.1f".format(v)
  val start=JalaliCalendar.format(JalaliCalendar.fromGregorian(Instant.ofEpochMilli(report.startEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()))
  val end=JalaliCalendar.format(JalaliCalendar.fromGregorian(Instant.ofEpochMilli(report.endEpochMillis-1).atZone(ZoneId.systemDefault()).toLocalDate()))
  text("نگاه مربی",80,55,1440,38f,cyan,true);text(report.memberName,80,115,1440,66f,white,true);text(report.title,80,205,1440,50f,gold,true)
  text("بازه گزارش: "+start+" تا "+end,80,275,1440,28f,muted);text("سطح فعلی: "+report.currentLevel+" • کل کتاب‌های تکمیل‌شده: "+report.completedBooksTotal,80,320,1440,26f,muted)
  report.metrics.forEachIndexed{idx,m->val col=idx%2;val row=idx/2;val x=80f+col*740f;val y=380f+row*145f;val accent=listOf(cyan,violet,pink,gold)[idx%4]
   card(x,y,690f,124f,accent);text(m.label,x.toInt()+22,y.toInt()+16,646,25f,muted,true)
   val value=if(m.isPercent)"%.1f".format(m.value)+"٪"else n(m.value);text(value+" "+m.unit,x.toInt()+22,y.toInt()+52,646,40f,accent,true)
   text("دوره قبل: "+n(m.previous),x.toInt()+22,y.toInt()+96,646,20f,muted)}
  val specs=listOf(Triple("روند XP",{b:AssistantReportBuilder.Bucket->b.xp},cyan),Triple("روند امتیاز",{b:AssistantReportBuilder.Bucket->b.points},violet),
   Triple("روند الماس",{b:AssistantReportBuilder.Bucket->b.diamonds},pink),Triple("مأموریت تکمیل‌شده",{b:AssistantReportBuilder.Bucket->b.missions.toDouble()},gold),
   Triple("پیشرفت نقشه کمال",{b:AssistantReportBuilder.Bucket->b.growth.toDouble()},cyan),Triple("صفحات کتاب تکمیل‌شده",{b:AssistantReportBuilder.Bucket->b.readingPages.toDouble()},violet),
   Triple("تغییر سطح ثبت‌شده",{b:AssistantReportBuilder.Bucket->b.levelChanges.toDouble()},pink))
  specs.forEachIndexed{idx,spec->val y=1010+idx*190;text(spec.first,80,y,1440,28f,white,true);val vals=report.series.map{spec.second(it)}
   val max=vals.maxOfOrNull{kotlin.math.abs(it)}?.coerceAtLeast(1.0)?:1.0;val left=110f;val base=y+115f;val step=1380f/vals.size.coerceAtLeast(1)
   c.drawLine(left,base,1490f,base,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0x55FFFFFF;strokeWidth=2f})
   vals.forEachIndexed{i,v->val bh=(kotlin.math.abs(v)/max*58.0).toFloat();val cx=left+(i+.5f)*step
    c.drawRoundRect(cx-minOf(30f,step*.25f),if(v>=0)base-bh else base,cx+minOf(30f,step*.25f),if(v>=0)base else base+bh,8f,8f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=spec.third})
    text(report.series[i].label,(cx-step/2).toInt(),y+130,step.toInt(),17f,muted,alignment=Layout.Alignment.ALIGN_CENTER)}}
  if(report.dataNotes.isNotEmpty())text(report.dataNotes.joinToString(" • "),80,2355,1440,20f,muted)
  text("منبع: سوابق ثبت‌شده در همین دستگاه • گزارش خودکار و مبتنی بر داده‌های موجود",80,2470,1440,20f,muted)
  val out=java.io.ByteArrayOutputStream();bmp.compress(Bitmap.CompressFormat.JPEG,93,out);bmp.recycle();return out.toByteArray()
 }
}
