package com.scopelocal.app.model

data class Student(
    val name: String,
    val gender: String,
    val dob: String,
    val registerNo: String,
    val institution: String,
    val program: String,
    val semester: String,
    val batch: String,
    val section: String,
    val campus: String,
    val parentName: String,
    val admissionDate: String,
    val cgpa: String,
    val sgpa: String,
    val photoUri: String?
)

data class Attendance(
    val code: String,
    val name: String,
    val faculty: String,
    val conducted: Int,
    val attended: Int
) {
    val absent: Int get() = (conducted - attended).coerceAtLeast(0)
    val percentage: Double get() = if (conducted == 0) 0.0 else attended * 100.0 / conducted
}

data class Mark(val code: String, val description: String, val acquired: Double, val maximum: Double)
data class Exam(val code: String, val description: String, val grade: String, val result: String)
data class Course(val code: String, val description: String, val credits: Int)

/**
 * A single class-day's hour-by-hour attendance record.
 *
 * [date] is stored/keyed in sortable "yyyy-MM-dd" form.
 * [hours] holds exactly 7 period entries, each one of:
 *   "P" = Present, "A" = Absent, "-" = No class / not applicable.
 *
 * This mirrors the Firestore document shape at
 * students/{studentId}/hourlyAttendance/{date} (fields hour1..hour7),
 * so a record can be pushed/pulled with a straightforward mapping.
 */
data class HourlyAttendance(
    val date: String,
    val hours: List<String>
) {
    val presentCount: Int get() = hours.count { it == "P" }
    val absentCount: Int get() = hours.count { it == "A" }

    companion object {
        const val PRESENT = "P"
        const val ABSENT = "A"
        const val NO_CLASS = "-"
        val STATES = listOf(PRESENT, ABSENT, NO_CLASS)
        const val PERIODS_PER_DAY = 7
    }
}

/** Aggregated P/A totals for one calendar month, driving the Hour Wise Attendance month rows. */
data class HourlyMonthSummary(
    val label: String,
    val present: Int,
    val absent: Int,
    val dates: List<String>
)
