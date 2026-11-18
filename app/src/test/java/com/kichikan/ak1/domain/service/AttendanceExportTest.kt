package com.kichikan.ak1.domain.service

import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.calendar.JalaliDate
import com.kichikan.ak1.domain.model.Attendance
import com.kichikan.ak1.domain.model.AttendanceStatus
import com.kichikan.ak1.domain.model.Member
import java.io.ByteArrayInputStream
import java.time.ZoneId
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceExportTest {
    private val zone = ZoneId.of("Asia/Tehran")

    @Test
    fun columnNamesFollowSpreadsheetConvention() {
        assertEquals("A", XlsxWriter.column(0))
        assertEquals("Z", XlsxWriter.column(25))
        assertEquals("AA", XlsxWriter.column(26))
        assertEquals("AB", XlsxWriter.column(27))
    }

    @Test
    fun workbookIsAZipWithTheExpectedPartsAndEscapedText() {
        val bytes = XlsxWriter.write("حضور", listOf(listOf("a<b&c", "غایب")))
        val names = mutableListOf<String>()
        var sheet = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e ->
                names += e.name
                if (e.name == "xl/worksheets/sheet1.xml") sheet = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml")))
        assertTrue(sheet.contains("a&lt;b&amp;c"))
        assertTrue(sheet.contains("rightToLeft=\"1\""))
    }

    @Test
    fun monthTableCountsAndKeepsReasonText() {
        val member = Member(id = "m1", ringId = "r", name = "علی")
        val day1 = JalaliCalendar.toGregorian(JalaliDate(1405, 8, 1)).atStartOfDay(zone).toInstant().toEpochMilli()
        val day2 = JalaliCalendar.toGregorian(JalaliDate(1405, 8, 2)).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = AttendanceSheet.rows(
            listOf(member),
            listOf(
                Attendance("a", "m1", day1, AttendanceStatus.PRESENT),
                Attendance("b", "m1", day2, AttendanceStatus.ABSENT, note = "بیمار"),
            ),
            1405, 8, zone,
        )
        assertEquals(2, rows.size)
        assertEquals(1 + 30 + 4, rows[0].size)
        assertEquals("حاضر", rows[1][1])
        assertEquals("غایب — بیمار", rows[1][2])
        assertEquals(listOf("1", "0", "1", "0"), rows[1].takeLast(4))
    }
}
