package com.sillynap.remutrack

import java.time.LocalDate
import java.time.YearMonth

enum class RemunerationType { INVIGILATION }
enum class ExamType { SEMESTER, BACKLOG, SHORT, OTHER }

data class RemunerationSettings(
    val type: RemunerationType = RemunerationType.INVIGILATION,
    val rate: Long = 0,
    val currency: String = "BDT"
)

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
    val updatedAt: Long = System.currentTimeMillis()
) {
    val displayExamType: String get() = if (examType == ExamType.OTHER) customExamType.orEmpty() else examType.name.lowercase().replaceFirstChar { it.uppercase() }
}

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
