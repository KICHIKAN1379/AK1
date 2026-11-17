package com.kichikan.ak1.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.HistoryType
import com.kichikan.ak1.domain.service.RingReport
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Builds the ring report image and the full ZIP (report + data + settings + puzzle pictures), and restores it. */
object ReportExport {
    private const val PREFS = "ak1_settings"

    /** The numbers of one report; [memberId] limits them to one member (named in the labels), null means the whole ring. */
    fun result(context: Context, repo: AppRepository, selection: RingReport.Selection, memberId: String? = null): RingReport.Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val itemsPerMember = GrowthStore.knowledge(prefs).size + GrowthStore.skills(prefs).size + GrowthStore.characters(prefs).size
        val zone = ZoneId.systemDefault()
        val members = repo.members.filter { memberId == null || it.id == memberId }
        val growthMillis = members.flatMap { m ->
            GrowthStore.unlocked(prefs, m.id).values.map { day -> LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli() }
        }
        val history = repo.history.filter { memberId == null || it.memberId == memberId }
        val subject = memberId?.let { id -> repo.members.firstOrNull { it.id == id }?.name }
        return RingReport.compute(selection, members, history, growthMillis, itemsPerMember, subject)
    }

    /** JPEG report image; for a member it carries that member's name, for the ring the ring name and photo. */
    fun jpeg(context: Context, repo: AppRepository, selection: RingReport.Selection, memberId: String? = null): ByteArray {
        val report = result(context, repo, selection, memberId)
        val ranges = RingReport.ranges(selection)
        val prizes = repo.history
            .filter { event ->
                (memberId == null || event.memberId == memberId) && event.type == HistoryType.REWARD_RECEIVED &&
                    ranges.any { event.createdAtEpochMillis >= it.start && event.createdAtEpochMillis < it.end }
            }
            .map { it.title }
        val member = memberId?.let { id -> repo.members.firstOrNull { it.id == id } }
        return renderJpeg(report, if (member == null) ringPhoto(context) else null, member?.name ?: repo.ring?.ringName ?: "حلقه", prizes, member != null)
    }

    private const val PROFILE_IMAGE_MARKER = "__AK1_ZIP_PROFILE_IMAGE__"
    private const val BANNER_IMAGE_MARKER = "__AK1_ZIP_BANNER_IMAGE__"

    private fun putDirectory(zip: ZipOutputStream, directory: File, prefix: String) {
        if (!directory.exists()) return
        directory.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(directory).invariantSeparatorsPath
            zip.putNextEntry(ZipEntry("$prefix/$relative"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun putRingImages(context: Context, put: (String, ByteArray) -> Unit): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val copied = mutableSetOf<String>()
        listOf(
            "ring_profile_image" to "ring/profile_image",
            "ring_home_banner" to "ring/home_banner"
        ).forEach { (key, entry) ->
            val raw = prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: return@forEach
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(raw))?.use { stream ->
                    put(entry, stream.readBytes())
                    copied += key
                }
            }
        }
        return copied
    }

    private fun ringPhoto(context: Context): Bitmap? = runCatching {
        val uri = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ring_profile_image", null) ?: return null
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun renderJpeg(result: RingReport.Result, ring: Bitmap?, ringName: String, prizes: List<String>, forMember: Boolean): ByteArray {
        val w = 1600
        val h = 2200
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(0xFF0B1020.toInt(), 0xFF1B1140.toInt(), 0xFF052E2B.toInt()), null, Shader.TileMode.CLAMP)
        })
        fun text(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) {
            c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textSize = size; textAlign = align
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            })
        }
        val gold = 0xFFFEC856.toInt()
        val soft = 0xFFC3D3F2.toInt()
        val cyan = 0xFF1EFCE5.toInt()

        if (ring != null) {
            val side = minOf(ring.width, ring.height)
            val src = android.graphics.Rect((ring.width - side) / 2, (ring.height - side) / 2, (ring.width + side) / 2, (ring.height + side) / 2)
            c.save()
            c.clipPath(android.graphics.Path().apply { addCircle(w / 2f, 200f, 110f, android.graphics.Path.Direction.CW) })
            c.drawBitmap(ring, src, android.graphics.RectF(w / 2f - 110f, 90f, w / 2f + 110f, 310f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
        text(ringName, w / 2f, 370f, 58f, Color_WHITE, bold = true)
        text(result.title, w / 2f, 450f, 66f, gold, bold = true)

        result.metrics.forEachIndexed { i, m ->
            val x = 60f + (i % 2) * 760f
            val y = 500f + (i / 2) * 210f
            c.drawRoundRect(x, y, x + 700f, y + 180f, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF })
            text(m.label, x + 350f, y + 60f, 34f, soft)
            val value = if (m.isPercent) "%.1f٪".format(m.value) else "%.1f".format(m.value)
            text(value, x + 350f, y + 130f, 56f, cyan, bold = true)
            val change = if (m.previous == 0.0) "—" else "%+.0f٪ نسبت به دوره‌ی قبل".format((m.value - m.previous) / m.previous * 100)
            text(change, x + 350f, y + 166f, 26f, soft)
        }

        // Share chart (bars with percentage labels) and the line through the same points.
        val left = 120f
        val right = w - 120f
        val top = 1400f
        val bottom = 1780f
        text(if (forMember) "روند دوره (سهم هر بازه از XP عضو)" else "روند دوره (سهم هر بازه از کل)", w / 2f, 1360f, 40f, gold, bold = true)
        val series = result.series
        if (series.isNotEmpty()) {
            val total = series.sumOf { it.second }
            val max = series.maxOf { it.second }
            val step = (right - left) / series.size
            val points = mutableListOf<Pair<Float, Float>>()
            series.forEachIndexed { i, (label, v) ->
                val cx = left + (i + 0.5f) * step
                val barH = if (max <= 0.0) 0f else ((v / max) * (bottom - top)).toFloat()
                c.drawRoundRect(cx - step * 0.3f, bottom - barH, cx + step * 0.3f, bottom, 14f, 14f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x6631D0FF })
                val share = if (total <= 0.0) 0.0 else v / total * 100
                text("%.0f٪".format(share), cx, bottom - barH - 14f, 28f, cyan, bold = true)
                text(label, cx, bottom + 44f, 26f, soft)
                points += cx to (bottom - barH)
            }
            if (points.size > 1) {
                val path = android.graphics.Path().apply {
                    moveTo(points[0].first, points[0].second)
                    points.drop(1).forEach { lineTo(it.first, it.second) }
                }
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 6f; color = gold
                })
            }
            points.forEach { (x, y) -> c.drawCircle(x, y, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }) }
        }

        text("جوایز دریافتی این دوره", 120f, 1900f, 40f, gold, bold = true, align = Paint.Align.LEFT)
        if (prizes.isEmpty()) text("جایزه‌ای در این دوره ثبت نشده است.", 120f, 1960f, 30f, soft, align = Paint.Align.LEFT)
        prizes.take(4).forEachIndexed { i, p -> text("• $p", 120f, 1960f + i * 50f, 30f, Color_WHITE, align = Paint.Align.LEFT) }
        text((if (forMember) "" else "همه عددها میانگین هر عضو یا درصد اعضا هستند؛ مستقل از تعداد اعضای حلقه • ") + "ساخته‌شده در AK1 • ${JalaliCalendar.format(JalaliCalendar.fromGregorian(LocalDate.now()))}", w / 2f, h - 60f, 26f, soft)

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private const val Color_WHITE = 0xFFFFFFFF.toInt()

    private fun settingsJson(context: Context, copiedRingImages: Set<String> = emptySet()): String {
        val arr = JSONArray()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            val o = JSONObject().put("k", k)
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is String -> {
                    val portable = when {
                        k == "ring_profile_image" -> if (k in copiedRingImages) PROFILE_IMAGE_MARKER else ""
                        k == "ring_home_banner" -> if (k in copiedRingImages) BANNER_IMAGE_MARKER else ""
                        else -> v
                    }
                    o.put("t", "s").put("v", portable)
                }
                is Set<*> -> o.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> return@forEach
            }
            arr.put(o)
        }
        return arr.toString()
    }

    /** Only the visual data: custom avatars, frames, item pictures, puzzle pictures and the background. */
    /** Streaming visual archive; avoids allocating the whole ZIP in memory on the UI thread. */
    fun writeVisualZip(context: Context, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            putDirectory(zip, File(context.filesDir, "custom_assets"), "custom_assets")
            putDirectory(zip, File(context.filesDir, "puzzle"), "puzzle")
            putDirectory(zip, File(context.filesDir, "background"), "background")
            putRingImages(context, ::put)
        }
    }

    /** Compatibility helper for callers that explicitly need an in-memory archive. */
    fun visualZip(context: Context): ByteArray {
        val bos = ByteArrayOutputStream()
        writeVisualZip(context, bos)
        return bos.toByteArray()
    }

    /** Restores a ZIP made by [build] or [visualZip]. Returns false when nothing in it is recognised. */
    fun restoreZip(context: Context, repo: AppRepository, uri: Uri): Boolean {
        var backup: String? = null
        var settings: String? = null
        val puzzle = mutableMapOf<String, ByteArray>()
        val assets = mutableMapOf<String, ByteArray>()
        val ringImages = mutableMapOf<String, ByteArray>()
        var background: ByteArray? = null
        val opened = context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    when {
                        e.name == "backup.json" -> backup = zip.readBytes().toString(Charsets.UTF_8)
                        e.name == "settings.json" -> settings = zip.readBytes().toString(Charsets.UTF_8)
                        e.name.startsWith("puzzle/") -> puzzle[File(e.name).name] = zip.readBytes()
                        e.name.startsWith("custom_assets/") -> {
                            val relative = e.name.removePrefix("custom_assets/")
                            if (relative.split('/').all { it.isNotBlank() && it != "." && it != ".." }) assets[relative] = zip.readBytes()
                        }
                        e.name == "ring/profile_image" -> ringImages["ring_profile_image"] = zip.readBytes()
                        e.name == "ring/home_banner" -> ringImages["ring_home_banner"] = zip.readBytes()
                        e.name == "background/custom.jpg" -> background = zip.readBytes()
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
            true
        } ?: false
        if (!opened) return false
        val data = backup
        if (data == null && assets.isEmpty() && puzzle.isEmpty() && ringImages.isEmpty() && background == null) return false
        background?.let { bytes ->
            File(context.filesDir, "background").apply { mkdirs() }.let { File(it, "custom.jpg").writeBytes(bytes) }
            com.kichikan.ak1.ui.theme.AppBackground.reload()
        }
        // Pictures come back into this phone's folder; the saved paths are rewritten to match.
        val assetDir = File(context.filesDir, "custom_assets").apply { mkdirs() }
        assets.forEach { (name, bytes) ->
            File(assetDir, name).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        }
        ringImages.forEach { (key, bytes) ->
            val fileName = if (key == "ring_profile_image") "ring_profile_image" else "ring_home_banner"
            val file = File(assetDir, fileName).apply { parentFile?.mkdirs() }
            file.writeBytes(bytes)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key, Uri.fromFile(file).toString()).apply()
        }
        if (data != null) {
            val fixed = Regex("""[^"]*/custom_assets/([^"]+)""").replace(data) { m -> File(assetDir, m.groupValues[1]).absolutePath }
            repo.importBackup(fixed)
        }
        settings?.let { restoreSettings(context, it) }
        val dir = File(context.filesDir, "puzzle").apply { mkdirs() }
        puzzle.forEach { (name, bytes) -> File(dir, name).writeBytes(bytes) }
        return true
    }

    private fun restoreSettings(context: Context, raw: String) {
        val arr = JSONArray(raw)
        val ed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val k = o.getString("k")
            when (o.getString("t")) {
                "b" -> ed.putBoolean(k, o.getBoolean("v"))
                "i" -> ed.putInt(k, o.getInt("v"))
                "l" -> ed.putLong(k, o.getLong("v"))
                "f" -> ed.putFloat(k, o.getDouble("v").toFloat())
                "s" -> {
                    val raw = o.getString("v")
                    val portable = when {
                        k == "ring_profile_image" && raw == PROFILE_IMAGE_MARKER ->
                            Uri.fromFile(File(context.filesDir, "custom_assets/ring_profile_image")).toString()
                        k == "ring_home_banner" && raw == BANNER_IMAGE_MARKER ->
                            Uri.fromFile(File(context.filesDir, "custom_assets/ring_home_banner")).toString()
                        else -> raw
                    }
                    ed.putString(k, portable)
                }
                "set" -> {
                    val s = o.getJSONArray("v")
                    ed.putStringSet(k, (0 until s.length()).map { s.getString(it) }.toSet())
                }
            }
        }
        ed.commit()
    }
}
