package com.kichikan.ak1.data

import android.app.Application
import android.content.Context
import android.net.Uri
import com.kichikan.ak1.ui.ReportExport
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReportExportZipIntegrationTest {
    private lateinit var context: Application

    @Before
    fun resetLocalStore() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ak1_local", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("ak1_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun fullRingZipRoundTripRestoresOrderedBackupAndCustomAssets() {
        val source = AppRepository(context)
        source.createRing("حلقه آرشیو", "archive-test")
        val first = source.addMember("عضو اول")
        val second = source.addMember("عضو دوم")
        source.recordXp(first.id, 25, "آزمون خروجی")
        source.recordXp(second.id, 50, "آزمون خروجی")

        val marker = File(context.filesDir, "custom_assets/round-trip/marker.txt")
        marker.parentFile?.mkdirs()
        marker.writeText("asset-round-trip")

        val archive = File(context.cacheDir, "full-ring-round-trip.zip")
        ReportExport.writeFullRingZip(context, source, byteArrayOf(1, 2, 3), archive.outputStream())
        assertTrue(archive.isFile)

        // Remove the original file and stored ring so the restore must use the ZIP contents.
        assertTrue(marker.delete())
        context.getSharedPreferences("ak1_local", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val restored = AppRepository(context)
        assertNull(restored.ring)

        assertTrue(ReportExport.restoreZip(context, restored, Uri.fromFile(archive)))

        assertEquals("حلقه آرشیو", restored.ring?.ringName)
        assertEquals(listOf("عضو اول", "عضو دوم"), restored.members.map { it.name })
        assertEquals(listOf(25, 50), restored.members.map { it.economy.xp })
        assertEquals(source.history.map { it.id }, restored.history.map { it.id })
        assertTrue(marker.exists())
        assertEquals("asset-round-trip", marker.readText())
    }
}
