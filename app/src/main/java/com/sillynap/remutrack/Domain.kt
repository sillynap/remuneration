package com.sillynap.remutrack

import java.time.LocalDate
import java.time.YearMonth

enum class RemunerationType { INVIGILATION }
enum class ExamType { SEMESTER, BACKLOG, SHORT, OTHER }
enum class DegreeYear(val label: String) { FIRST("1st"), SECOND("2nd"), THIRD("3rd"), FOURTH("4th") }
enum class Semester(val label: String) { ODD("Odd"), EVEN("Even") }

data class RemunerationSettings(
    val type: RemunerationType = RemunerationType.INVIGILATION,
    val rate: Long = 0,
    val currency: String = "BDT"
)

object SettingsCodec {
    fun encode(settings: RemunerationSettings): String = "${settings.rate}|${settings.currency}"

    fun decode(raw: String?): RemunerationSettings? {
        val parts = raw?.split('|', limit = 2) ?: return null
        val rate = parts.firstOrNull()?.toLongOrNull() ?: return null
        val currency = parts.getOrNull(1)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        return RemunerationSettings(rate = rate, currency = currency)
    }
}

data class InvigilationEntry(
    val id: String,
    val workDate: LocalDate,
    val examSeries: String,
    val examYear: Int,
    val examType: ExamType,
    val customExamType: String? = null,
    val rate: Long,
    val paid: Boolean = false,
    val paymentDate: LocalDate? = null,
    val paymentNote: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val degreeYear: DegreeYear? = null,
    val semester: Semester? = null
) {
    val displayExamType: String get() = if (examType == ExamType.OTHER) customExamType.orEmpty() else examType.name.lowercase().replaceFirstChar { it.uppercase() }
}

fun InvigilationEntry.title(): String =
    if (examType == ExamType.SEMESTER && degreeYear != null && semester != null) {
        "$examSeries Series, ${degreeYear.label} year ${semester.label} semester examination $examYear"
    } else {
        "$examSeries Series, $displayExamType examination $examYear"
    }

fun InvigilationEntry.withPaymentStatus(paid: Boolean, paymentDate: LocalDate? = if (paid) LocalDate.now() else null): InvigilationEntry =
    copy(paid = paid, paymentDate = paymentDate, updatedAt = System.currentTimeMillis())

data class Summary(
    val unpaidCount: Int,
    val unpaidAmount: Long,
    val paidCount: Int,
    val paidAmount: Long,
    val monthAmount: Long
)

object RemunerationCalculator {
    fun summary(entries: List<InvigilationEntry>, month: YearMonth = YearMonth.now()): Summary {
        val unpaid = entries.filterNot { it.paid }
        val paid = entries.filter { it.paid }
        return Summary(unpaid.size, unpaid.sumOf { it.rate }, paid.size, paid.sumOf { it.rate },
            entries.filter { YearMonth.from(it.workDate) == month }.sumOf { it.rate })
    }

    fun payableInRange(entries: List<InvigilationEntry>, from: LocalDate, to: LocalDate): List<InvigilationEntry> =
        entries.filter { !it.paid && it.workDate >= from && it.workDate <= to }
}
