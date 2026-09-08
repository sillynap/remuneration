package com.sillynap.remutrack

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RemunerationCalculatorTest {
    private fun entry(date: String, rate: Long, paid: Boolean = false) = InvigilationEntry("id$date$rate", LocalDate.parse(date), "Series", 2026, ExamType.SEMESTER, rate = rate, paid = paid)
    @Test fun rangeIsInclusiveAndExcludesPaid() {
        val result = RemunerationCalculator.payableInRange(listOf(entry("2026-01-01", 100), entry("2026-01-03", 200), entry("2026-01-04", 300, true)), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-03"))
        assertEquals(listOf(100L, 200L), result.map { it.rate })
    }
    @Test fun summarySeparatesPaidAndUnpaid() {
        val s = RemunerationCalculator.summary(listOf(entry("2026-01-01", 100), entry("2026-01-02", 250, true)))
        assertEquals(1, s.unpaidCount); assertEquals(100, s.unpaidAmount); assertEquals(1, s.paidCount); assertEquals(250, s.paidAmount)
    }

    @Test fun settingsCodecRoundTripsRateAndCurrency() {
        val settings = RemunerationSettings(rate = 850, currency = "bdt")
        assertEquals(settings.copy(currency = "BDT"), SettingsCodec.decode(SettingsCodec.encode(settings)))
    }

    @Test fun settingsCodecRejectsMalformedValues() {
        assertEquals(null, SettingsCodec.decode("not-a-rate|BDT"))
        assertEquals(null, SettingsCodec.decode("850|"))
    }

    @Test fun paymentStatusTransitionClearsDateWhenReturningToUnpaid() {
        val paid = entry("2026-01-01", 100).withPaymentStatus(true, LocalDate.parse("2026-01-05"))
        val unpaid = paid.withPaymentStatus(false)
        assertEquals(true, paid.paid)
        assertEquals(LocalDate.parse("2026-01-05"), paid.paymentDate)
        assertEquals(false, unpaid.paid)
        assertEquals(null, unpaid.paymentDate)
    }

    @Test fun semesterTitleIncludesDegreeYearAndSemester() {
        val semester = entry("2026-01-01", 100).copy(
            examSeries = "Spring",
            examYear = 2026,
            examType = ExamType.SEMESTER,
            degreeYear = DegreeYear.SECOND,
            semester = Semester.ODD
        )
        assertEquals("Spring Series, 2nd year Odd semester examination 2026", semester.title())
    }

    @Test fun nonSemesterTitleOmitsSemesterFields() {
        val backlog = entry("2026-01-01", 100).copy(
            examSeries = "Spring",
            examType = ExamType.BACKLOG,
            degreeYear = DegreeYear.FOURTH,
            semester = Semester.EVEN
        )
        assertEquals("Spring Series, Backlog examination 2026", backlog.title())
    }

    @Test fun backlogTitleIncludesDegreeYearAndSemester() {
        val backlog = entry("2026-01-01", 100).copy(
            examSeries = "Spring",
            examType = ExamType.BACKLOG,
            degreeYear = DegreeYear.THIRD,
            semester = Semester.EVEN
        )
        assertEquals("Spring Series, 3rd year Even semester examination 2026", backlog.title())
    }

    @Test fun legacyBacklogWithoutSemesterFieldsRemainsReadable() {
        val backlog = entry("2026-01-01", 100).copy(examType = ExamType.BACKLOG)
        assertEquals("Series Series, Backlog examination 2026", backlog.title())
    }
}
