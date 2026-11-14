package com.kichikan.ak1.data
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
/** Read-only adapter for existing Growth Map preferences. */
internal object AssistantDataProvider {
 fun growthLastAction(context:Context,memberIds:Set<String>):Map<String,Map<String,Long>>{
  val p=context.applicationContext.getSharedPreferences("ak1_settings",Context.MODE_PRIVATE)
  fun names(key:String,default:List<String> = emptyList()):List<String> = runCatching{val raw=p.getString(key,null)?:return@runCatching default;val a=JSONArray(raw);(0 until a.length()).map{a.getString(it)}}.getOrDefault(default)
  val knowledge=names("growth_items_knowledge");val skills=names("growth_items_skill")
  val virtues=names("growth_items_character",listOf("صداقت","زبان نیک","صبر","ادب و احترام","اول سلام","بخشنده","خادم بی‌منت","نماز اول وقت","نماز جماعت‌خوان","مسئولیت‌پذیر","عزت‌نفس","اعتماد به نفس","پیش‌قدم در کار خیر","متواضع","با حیا"))
  return memberIds.associateWith{id->
   val log=runCatching{JSONObject(p.getString("growth_log_"+id,"{}")?:"{}")}.getOrDefault(JSONObject())
   fun latest(items:List<String>,category:String):Long?=items.asSequence().filter{p.getBoolean("growth_active_"+category+"_"+it,true)&&log.has(it)}.mapNotNull{n->runCatching{log.getLong(n)*86_400_000L}.getOrNull()}.maxOrNull()
   val ability=listOfNotNull(latest(knowledge,"knowledge"),latest(skills,"skills")).maxOrNull()
   buildMap{if(ability!=null)put("توانمندی",ability);latest(virtues,"characters")?.let{put("خودسازی",it)}}
  }
 }
}