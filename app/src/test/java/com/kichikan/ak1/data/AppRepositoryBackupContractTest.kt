package com.kichikan.ak1.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AppRepositoryBackupContractTest {
    private lateinit var context: Application

    @Before
    fun resetLocalStore() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ak1_local", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun emptyBackupImportsAsAnEmptyRing() {
        val repository = AppRepository(context)

        repository.importBackup("""{"schemaVersion":10}""")

        assertNull(repository.ring)
        assertTrue(repository.members.isEmpty())
        assertTrue(repository.history.isEmpty())
        assertTrue(repository.shop.isEmpty())
    }

    @Test
    fun exportImportRoundTripPreservesMemberAndHistoryOrder() {
        val original = AppRepository(context)
        original.createRing("حلقه آزمون", "test-ring")
        val first = original.addMember("عضو اول")
        val second = original.addMember("عضو دوم")
        original.recordXp(first.id, 25, "ثبت آزمون")
        original.recordXp(second.id, 50, "ثبت آزمون")

        val backup = original.exportBackup()
        assertTrue(backup.contains("\"schemaVersion\":10"))

        val restored = AppRepository(context)
        restored.importBackup(backup)

        assertEquals("حلقه آزمون", restored.ring?.ringName)
        assertEquals(listOf("عضو اول", "عضو دوم"), restored.members.map { it.name })
        assertEquals(listOf(25, 50), restored.members.map { it.economy.xp })
        assertEquals(original.history.map { it.id }, restored.history.map { it.id })
    }

    @Test
    fun legacySchemaNineStillLoadsAndCreatesOnlyTheMonitoringBaseline() {
        val legacy = """
            {
              "schemaVersion": 9,
              "ringId": "legacy-ring",
              "ringName": "حلقه قدیمی",
              "ringUsername": "legacy",
              "members": [
                {"id":"legacy-member","ringId":"legacy-ring","name":"عضو قدیمی","xp":0,"points":0,"diamonds":0}
              ],
              "history": []
            }
        """.trimIndent()

        val repository = AppRepository(context)
        repository.importBackup(legacy)

        assertEquals("حلقه قدیمی", repository.ring?.ringName)
        val member = repository.members.single()
        assertEquals(0L, member.registeredAtEpochMillis)
        assertNotNull(member.monitoringStartAtEpochMillis)
        assertTrue((member.monitoringStartAtEpochMillis ?: 0L) > 0L)
    }

    @Test
    fun invalidImportRollsBackSavedBackupAndLeavesLiveStateUntouched() {
        val repository = AppRepository(context)
        repository.createRing("حلقه سالم", "healthy")
        val member = repository.addMember("عضو سالم")
        val previousBackup = repository.exportBackup()

        assertThrows(IllegalArgumentException::class.java) {
            repository.importBackup("""{"schemaVersion":10,"members":[{"id":"broken"}]}""")
        }

        assertEquals(previousBackup, repository.exportBackup())
        assertEquals(listOf(member.id), repository.members.map { it.id })
        assertFalse(repository.members.isEmpty())
    }
}
