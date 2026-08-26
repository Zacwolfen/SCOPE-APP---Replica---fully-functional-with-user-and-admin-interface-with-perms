package com.scopelocal.app.data

import android.content.ContentValues
import android.content.Context
import java.util.Calendar
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.scopelocal.app.model.Attendance
import com.scopelocal.app.model.Exam
import com.scopelocal.app.model.HourlyAttendance
import com.scopelocal.app.model.HourlyMonthSummary
import com.scopelocal.app.model.Mark
import com.scopelocal.app.model.Student

class LocalStore(context: Context) :
    SQLiteOpenHelper(context, "scope_local.db", null, 7) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT NOT NULL)")
        seed(db)
        seedHistoricalHourlyAttendance(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            put(db, "attendance_last_date", dateKey(Calendar.getInstance()))
        }
        if (oldVersion < 3) {
            seedHourlyAttendanceIfMissing(db)
        }
        if (oldVersion < 4) {
            removeObsoleteHourlyAttendance(db)
            ensureAttendanceBaseline(db)
        }
        if (oldVersion < 5) {
            seedHistoricalHourlyAttendance(db)
        }
        // Re-run the bounded historical backfill when upgrading from the
        // previous historical-seed build. The seed only fills missing dates
        // from 20-Jul-2026 through 25-Aug-2026 and never touches 26-Aug onward.
        if (oldVersion < 6) {
            seedHistoricalHourlyAttendance(db)
        }
        if (oldVersion < 7) {
            seedHistoricalHourlyAttendance(db)
            ensureAttendanceBaseline(db)
        }
    }

    override fun onDowngrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // A device can end up with a stored version newer than this build declares
        // (e.g. after testing a different build). The default behavior is to throw
        // and the app can never open again, so recover instead: recreate the table.
        // This only affects locally-cached demo data - cloud data is unaffected and
        // will re-sync on next Firestore listener callback / refresh.
        db.execSQL("DROP TABLE IF EXISTS kv")
        onCreate(db)
    }

    /**
     * One-time historical seed for 20-Jul-2026 through 25-Aug-2026.
     *
     * July gets 6 or 7 scheduled classes marked Present on weekdays.
     * August 1-25 gets every scheduled class marked Present.
     * Weekends are still represented as dates, but contain no classes.
     *
     * This is intentionally bounded at 25-Aug-2026 and never overwrites an
     * existing date, so 26-Aug-2026 onward remains fully editable.
     */
    private fun seedHistoricalHourlyAttendance(db: SQLiteDatabase) {
        val start = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JULY, 20)
        }
        val end = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.AUGUST, 25)
        }

        val random = java.util.Random(20260825L)
        val existingDates = getDbValue(db, "hourly_dates")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        val deletedDates = getDbValue(db, "hourly_deleted_dates")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()

        val cursorDate = start.clone() as Calendar

        while (!cursorDate.after(end)) {
            val date = dateKey(cursorDate)

            // Never overwrite anything already entered/synced by the admin.
            if (date !in existingDates && date !in deletedDates) {
                val schedule = timetableForDay(cursorDate.get(Calendar.DAY_OF_WEEK))
                val hours = MutableList(HourlyAttendance.PERIODS_PER_DAY) {
                    HourlyAttendance.NO_CLASS
                }

                val isWeekend =
                    cursorDate.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                        cursorDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

                if (!isWeekend) {
                    if (cursorDate.get(Calendar.MONTH) == Calendar.JULY) {
                        // The app currently has 7 periods/day, so 6 or 7 is
                        // the valid range of actual scheduled classes.
                        val scheduledPeriods = schedule.indices
                            .filter { schedule[it] != "-" }

                        val classCount = minOf(
                            6 + random.nextInt(2),
                            scheduledPeriods.size
                        )

                        scheduledPeriods
                            .shuffled(random)
                            .take(classCount)
                            .forEach { period ->
                                hours[period] = HourlyAttendance.PRESENT
                            }
                    } else {
                        // 1-Aug through 25-Aug: every scheduled class is Present.
                        schedule.forEachIndexed { period, subject ->
                            if (subject != "-") {
                                hours[period] = HourlyAttendance.PRESENT
                            }
                        }
                    }
                }

                hours.forEachIndexed { period, state ->
                    put(
                        db,
                        "hourly_${date}_h${period + 1}",
                        state
                    )
                }

                existingDates.add(date)
            }

            cursorDate.add(Calendar.DAY_OF_MONTH, 1)
        }

        put(
            db,
            "hourly_dates",
            existingDates
                .filter { it >= HOURLY_START_DATE }
                .distinct()
                .sortedDescending()
                .joinToString(",")
        )

        ensureAttendanceBaseline(db)
    }

    /**
     * Repairs only missing bounded historical records. Existing dates/hours and all
     * future records remain untouched. This makes the seeded history resilient to
     * reinstall/upgrade/sync workflows without using it as a source of future data.
     */
    fun ensureHistoricalHourlyAttendance() {
        writableDatabase.use { db ->
            seedHistoricalHourlyAttendance(db)
            ensureAttendanceBaseline(db)
        }
        recalculateAttendanceFromHourly()
    }

    /** Upgrade old installations without restoring obsolete Jan-Apr hourly data. */
    private fun seedHourlyAttendanceIfMissing(db: SQLiteDatabase) {
        if (getDbValue(db, "hourly_dates").isBlank()) put(db, "hourly_dates", "")
        ensureAttendanceBaseline(db)
        if (getDbValue(db, "last_update_epoch").isBlank()) {
            put(db, "last_update_epoch", System.currentTimeMillis().toString())
        }
    }

    private fun seed(db: SQLiteDatabase) {

        val defaults: Map<String, String> = mapOf(
            "name" to "Sreya S",
            "gender" to "Female",
            "dob" to "29-Nov-2007",
            "register" to "RA2411003040006",
            "institution" to "Faculty of Engineering and Technology, Vadapalani (SRM Institute of Science and Technology)",
            "program" to "B.Tech.-Computer Science and Engineering [UG - FT - ACADEMIC]",
            "semester" to "V SEMESTER",
            "batch" to "2024",
            "section" to "B",
            "campus" to "Vadapalani",
            "parentName" to "AnanthaLakshmi S",
            "admissionDate" to "06-Aug-2024",
            "cgpa" to "10",
            "sgpa" to "9.42",
            "photoUri" to ""
        )

        defaults.forEach { entry ->
            put(db, entry.key, entry.value)
        }

        val att = listOf(
            Attendance(
                "21CSC301T",
                "FORMAL LANGUAGE AND AUTOMATA",
                "",
                19,
                17
            ),
            Attendance(
                "21CSC302J",
                "COMPUTER NETWORKS",
                "",
                29,
                27
            ),
            Attendance(
                "21CSC305P",
                "MACHINE LEARNING",
                "",
                16,
                14
            ),
            Attendance(
                "21CSE354T",
                "FULL STACK WEB DEVELOPMENT",
                "",
                16,
                13
            ),
            Attendance(
                "21GNP301L",
                "COMMUNITY CONNECT",
                "",
                3,
                3
            ),
            Attendance(
                "21LEM301T",
                "INDIAN ART FORM",
                "",
                4,
                4
            ),
            Attendance(
                "21MAB302T",
                "DISCRETE MATHEMATICS",
                "",
                15,
                12
            ),
            Attendance(
                "21MEO115T",
                "INTRODUCTION TO DRONES",
                "",
                15,
                13
            )
        )

        att.forEachIndexed { i, a ->
            put(db, "att_${i}_code", a.code)
            put(db, "att_${i}_name", a.name)
            put(db, "att_${i}_conducted", a.conducted.toString())
            put(db, "att_${i}_attended", a.attended.toString())
        }

        val marks = listOf(
            Mark(
                "21CSC206T",
                "ARTIFICIAL INTELLIGENCE",
                57.08,
                60.0
            ),
            Mark(
                "21CSC205P",
                "DATABASE MANAGEMENT SYSTEMS",
                94.40,
                100.0
            ),
            Mark(
                "21CSC204J",
                "DESIGN AND ANALYSIS OF ALGORITHMS",
                48.88,
                60.0
            ),
            Mark(
                "21CSE253T",
                "INTERNET OF THINGS",
                50.0,
                60.0
            ),
            Mark(
                "21MAB204T",
                "PROBABILITY AND QUEUEING THEORY",
                48.05,
                60.0
            ),
            Mark(
                "21PDH209T",
                "SOCIAL ENGINEERING",
                95.0,
                100.0
            ),
            Mark(
                "21LEM202T",
                "UNIVERSAL HUMAN VALUES - II: UNDERSTANDING HARMONY AND ETHICAL HUMAN CONDUCT",
                100.0,
                100.0
            )
        )

        marks.forEachIndexed { i, m ->
            put(db, "mark_${i}_code", m.code)
            put(db, "mark_${i}_desc", m.description)
            put(db, "mark_${i}_acq", m.acquired.toString())
            put(db, "mark_${i}_max", m.maximum.toString())
        }

        val exams = listOf(
            Exam(
                "21MAB204T",
                "PROBABILITY AND QUEUEING THEORY",
                "O",
                "Pass"
            ),
            Exam(
                "21CSC204J",
                "DESIGN AND ANALYSIS OF ALGORITHMS",
                "O",
                "Pass"
            ),
            Exam(
                "21CSC205P",
                "DATABASE MANAGEMENT SYSTEMS",
                "O",
                "Pass"
            ),
            Exam(
                "21CSC206T",
                "ARTIFICIAL INTELLIGENCE",
                "O",
                "Pass"
            ),
            Exam(
                "21CSE253T",
                "INTERNET OF THINGS",
                "O",
                "Pass"
            ),
            Exam(
                "21PDH209T",
                "SOCIAL ENGINEERING",
                "O",
                "Pass"
            ),
            Exam(
                "21LEM202T",
                "UNIVERSAL HUMAN VALUES - II: UNDERSTANDING HARMONY AND ETHICAL HUMAN CONDUCT",
                "O",
                "Pass"
            )
        )

        exams.forEachIndexed { i, e ->
            put(db, "exam_${i}_code", e.code)
            put(db, "exam_${i}_desc", e.description)
            put(db, "exam_${i}_grade", e.grade)
            put(db, "exam_${i}_result", e.result)
        }

        // Hourly attendance is now an admin/cloud-controlled dataset. New installs start
        // empty and accept records from 20-Jul-2026 onward.
        put(db, "hourly_dates", "")
        put(db, "hourly_deleted_dates", "")
        for (i in 0 until 8) {
            put(db, "att_${i}_base_conducted", getDbValue(db, "att_${i}_conducted"))
            put(db, "att_${i}_base_attended", getDbValue(db, "att_${i}_attended"))
        }

        put(db, "last_update_epoch", System.currentTimeMillis().toString())
    }

    private fun removeObsoleteHourlyAttendance(db: SQLiteDatabase) {
        val dates = getDbValue(db, "hourly_dates")
            .split(",").map { it.trim() }
            .filter { it.isNotBlank() && it >= HOURLY_START_DATE }
            .distinct().sortedDescending()
        // Remove every Jan-Apr 2026 hourly cell and fallback aggregate.
        val allKeys = db.rawQuery("SELECT k FROM kv WHERE k LIKE 'hourly_%'", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        allKeys.filter { key ->
            Regex("hourly_2026-(01|02|03|04)-.*").matches(key) ||
                key == "hourly_fallback_2026-01_p" || key == "hourly_fallback_2026-01_a" ||
                key == "hourly_fallback_2026-02_p" || key == "hourly_fallback_2026-02_a" ||
                key == "hourly_fallback_2026-03_p" || key == "hourly_fallback_2026-03_a" ||
                key == "hourly_fallback_2026-04_p" || key == "hourly_fallback_2026-04_a"
        }.forEach { db.delete("kv", "k=?", arrayOf(it)) }
        put(db, "hourly_dates", dates.joinToString(","))
    }

    private fun getDbValue(db: SQLiteDatabase, key: String): String =
        db.rawQuery("SELECT v FROM kv WHERE k=?", arrayOf(key)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else ""
        }

    private fun ensureAttendanceBaseline(db: SQLiteDatabase) {
        for (i in 0 until 8) {
            if (getDbValue(db, "att_${i}_base_conducted").isBlank()) {
                put(db, "att_${i}_base_conducted", getDbValue(db, "att_${i}_conducted").ifBlank { "0" })
                put(db, "att_${i}_base_attended", getDbValue(db, "att_${i}_attended").ifBlank { "0" })
            }
        }
    }

    private fun put(
        db: SQLiteDatabase,
        key: String,
        value: String
    ) {
        db.insertWithOnConflict(
            "kv",
            null,
            ContentValues().apply {
                put("k", key)
                put("v", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun put(
        key: String,
        value: String
    ) {
        writableDatabase.use { db ->
            put(db, key, value)
        }
    }

    fun get(
        key: String,
        fallback: String = ""
    ): String {
        return readableDatabase.rawQuery(
            "SELECT v FROM kv WHERE k=?",
            arrayOf(key)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                fallback
            }
        }
    }

    fun student(): Student {
        return Student(
            get("name"),
            get("gender"),
            get("dob"),
            get("register"),
            get("institution"),
            get("program"),
            get("semester"),
            get("batch"),
            get("section"),
            get("campus"),
            get("parentName"),
            get("admissionDate"),
            get("cgpa"),
            get("sgpa"),
            get("photoUri").ifBlank { null }
        )
    }

    fun attendance(): List<Attendance> {
        return (0 until 8).map { i ->
            Attendance(
                get("att_${i}_code"),
                get("att_${i}_name"),
                "",
                get("att_${i}_conducted").toIntOrNull() ?: 0,
                get("att_${i}_attended").toIntOrNull() ?: 0
            )
        }
    }

    /**
     * Attendance is deterministic: the registered timetable defines which subject occupies
     * each hourly period, while the hourly P/A record defines whether that scheduled class was
     * attended. Recalculation never increments a counter; it derives totals from the baseline
     * plus the actual hourly records, so refresh/recomposition/restart cannot double-count.
     */
    fun applyAutomaticAttendance() {
        ensureAttendanceBaselineInCurrentStore()
        recalculateAttendanceFromHourly()
    }

    private fun ensureAttendanceBaselineInCurrentStore() {
        for (i in 0 until 8) {
            if (get("att_${i}_base_conducted").isBlank()) {
                put("att_${i}_base_conducted", get("att_${i}_conducted", "0"))
                put("att_${i}_base_attended", get("att_${i}_attended", "0"))
            }
        }
    }

    fun recalculateAttendanceFromHourly() {
        ensureAttendanceBaselineInCurrentStore()
        val conducted = IntArray(8) { get("att_${it}_base_conducted").toIntOrNull() ?: 0 }
        val attended = IntArray(8) { get("att_${it}_base_attended").toIntOrNull() ?: 0 }

        hourlyDates().filter { it >= HOURLY_START_DATE }.forEach { date ->
            val day = parseDate(date) ?: return@forEach
            val schedule = timetableForDay(day.get(Calendar.DAY_OF_WEEK))
            hourlyHours(date).forEachIndexed { period, state ->
                val subject = schedule.getOrNull(period) ?: "-"
                val index = when (subject) {
                    "FLA" -> 0
                    "CN", "CN LAB" -> 1
                    "ML", "ML LAB" -> 2
                    "DM" -> 6
                    "PE-2" -> 7
                    "OE-1" -> 3
                    "CC" -> 4
                    "IAF" -> 5
                    else -> null
                } ?: return@forEachIndexed
                
                when (state) {
                    HourlyAttendance.PRESENT, HourlyAttendance.ABSENT -> {
                        conducted[index]++
                        if (state == HourlyAttendance.PRESENT) attended[index]++
                    }
                }
            }
        }
    
        for (i in 0 until 8) {
            put("att_${i}_conducted", conducted[i].toString())
            put("att_${i}_attended", attended[i].toString())
        }
    }

    private fun timetableForDay(dayOfWeek: Int): List<String> = when (dayOfWeek) {
        Calendar.MONDAY -> listOf("CN", "OE-1", "FLA", "DM", "CC", "CN LAB", "CN LAB")
        Calendar.TUESDAY -> listOf("DM", "FLA", "ML", "CN LAB", "CN LAB", "PE-2", "-")
        Calendar.WEDNESDAY -> listOf("FLA", "ML LAB", "ML LAB", "CN", "IAF", "OE-1", "-")
        Calendar.THURSDAY -> listOf("PE-2", "CN", "ML", "OE-1", "FLA", "CN", "DM")
        Calendar.FRIDAY -> listOf("OE-1", "FLA", "DM", "PE-2", "PE-2", "LIB", "-")
        Calendar.SATURDAY, Calendar.SUNDAY -> emptyList()
        else -> emptyList()
    }

    private fun dateKey(calendar: Calendar): String {
        return String.format(
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun parseDate(value: String): Calendar? {
        val parts = value.split("-")
        if (parts.size != 3) return null

        return try {
            Calendar.getInstance().apply {
                clear()
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sameDate(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    fun attendanceBaseline(i: Int): Pair<Int, Int> {
        require(i in 0 until 8) { "Invalid attendance index" }
        ensureAttendanceBaselineInCurrentStore()
        return Pair(
            get("att_${i}_base_conducted").toIntOrNull() ?: 0,
            get("att_${i}_base_attended").toIntOrNull() ?: 0
        )
    }

    fun setAttendanceBaselineFromCloud(i: Int, conducted: Int, attended: Int) {
        require(i in 0 until 8) { "Invalid attendance index" }
        put("att_${i}_base_conducted", conducted.toString())
        put("att_${i}_base_attended", attended.toString())
    }

    /**
     * Manual regular-attendance editing changes the non-hourly baseline while keeping
     * every real hourly P/A record intact. This prevents the aggregate edit from
     * double-counting the hourly ledger.
     */
    fun updateAttendance(i: Int, conducted: Int, attended: Int) {
        require(i in 0 until 8) { "Invalid attendance index" }
        require(conducted >= 0) { "Conducted classes cannot be negative" }
        require(attended >= 0) { "Attended classes cannot be negative" }
        require(attended <= conducted) {
            "Attended classes cannot exceed conducted classes"
        }

        val (hourlyConducted, hourlyAttended) = hourlyContributionForSubject(i)
        require(conducted >= hourlyConducted) {
            "Total conducted cannot be lower than recorded hourly classes"
        }
        require(attended >= hourlyAttended) {
            "Total attended cannot be lower than recorded hourly presents"
        }

        put(
            "att_${i}_base_conducted",
            (conducted - hourlyConducted).toString()
        )
        put(
            "att_${i}_base_attended",
            (attended - hourlyAttended).toString()
        )
        recalculateAttendanceFromHourly()
    }

    private fun hourlyContributionForSubject(index: Int): Pair<Int, Int> {
        var conducted = 0
        var attended = 0

        hourlyDates().filter { it >= HOURLY_START_DATE }.forEach { date ->
            val day = parseDate(date) ?: return@forEach
            val schedule = timetableForDay(day.get(Calendar.DAY_OF_WEEK))

            hourlyHours(date).forEachIndexed { period, state ->
                val subject = schedule.getOrNull(period) ?: "-"
                val mapped = when (subject) {
                    "FLA" -> 0
                    "CN", "CN LAB" -> 1
                    "ML", "ML LAB" -> 2
                    "DM" -> 6
                    "PE-2" -> 7
                    "OE-1" -> 3
                    "CC" -> 4
                    "IAF" -> 5
                    else -> null
                }

                if (mapped == index && state != HourlyAttendance.NO_CLASS) {
                    conducted++
                    if (state == HourlyAttendance.PRESENT) attended++
                }
            }
        }

        return Pair(conducted, attended)
    }

    fun cloudStudentId(): String = get("cloudStudentId", "DEMO001")

    fun setCloudStudentId(studentId: String) {
        put("cloudStudentId", studentId.trim())
    }

    fun marks(): List<Mark> {
        return (0 until 7).map { i ->
            Mark(
                get("mark_${i}_code"),
                get("mark_${i}_desc"),
                get("mark_${i}_acq").toDoubleOrNull() ?: 0.0,
                get("mark_${i}_max").toDoubleOrNull() ?: 100.0
            )
        }
    }

    fun updateMark(
        i: Int,
        acquired: Double
    ) {
        put(
            "mark_${i}_acq",
            acquired.toString()
        )
    }

    fun exams(): List<Exam> {
        return (0 until 7).map { i ->
            Exam(
                get("exam_${i}_code"),
                get("exam_${i}_desc"),
                get("exam_${i}_grade"),
                get("exam_${i}_result")
            )
        }
    }

    fun updateGrade(
        i: Int,
        grade: String
    ) {
        put(
            "exam_${i}_grade",
            grade
        )
    }

    // ---------------------------------------------------------------------
    // Hourly attendance (admin-editable, Firestore-synchronized)
    // ---------------------------------------------------------------------

    /** All dates ("yyyy-MM-dd") that have an hourly attendance record, newest first. */
    fun hourlyDates(): List<String> {
        return get("hourly_dates")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedDescending()
    }

    private fun addHourlyDateToIndex(date: String) {
        val updated = (hourlyDates() + date).distinct().sortedDescending()
        put("hourly_dates", updated.joinToString(","))
    }

    /** The 7 period states ("P"/"A"/"-") for a given date, defaulting to "-" if unset. */
    fun hourlyHours(date: String): List<String> {
        return (1..HourlyAttendance.PERIODS_PER_DAY).map { period ->
            get("hourly_${date}_h$period", HourlyAttendance.NO_CLASS)
        }
    }

    /** Admin-facing write: set/replace all 7 hours for a date and register it in the index. */
    fun setHourlyHours(date: String, hours: List<String>) {
        require(hours.size == HourlyAttendance.PERIODS_PER_DAY) {
            "Expected ${HourlyAttendance.PERIODS_PER_DAY} hour entries"
        }
        hours.forEachIndexed { i, h ->
            put("hourly_${date}_h${i + 1}", if (h in HourlyAttendance.STATES) h else HourlyAttendance.NO_CLASS)
        }
        addHourlyDateToIndex(date)
        removeHourlyDeletionTombstone(date)
        recalculateAttendanceFromHourly()
    }

    /** Update a single hour/period for a date (used by the admin's per-hour editor). */
    fun setHourlyHour(date: String, periodIndex: Int, state: String) {
        require(periodIndex in 0 until HourlyAttendance.PERIODS_PER_DAY) { "Invalid period index" }
        val safeState = if (state in HourlyAttendance.STATES) state else HourlyAttendance.NO_CLASS
        put("hourly_${date}_h${periodIndex + 1}", safeState)
        addHourlyDateToIndex(date)
        removeHourlyDeletionTombstone(date)
        recalculateAttendanceFromHourly()
    }

    private fun removeHourlyDeletionTombstone(date: String) {
        val remaining = get("hourly_deleted_dates")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != date }
            .distinct()
        put("hourly_deleted_dates", remaining.joinToString(","))
    }

    /** Delete an entire date, including its underlying hourly cells. */
    fun deleteHourlyDate(date: String) {
        writableDatabase.use { db ->
            (1..HourlyAttendance.PERIODS_PER_DAY).forEach { period ->
                db.delete("kv", "k=?", arrayOf("hourly_${date}_h$period"))
            }
            val dates = hourlyDates().filterNot { it == date }
            put(db, "hourly_dates", dates.joinToString(","))
            val deleted = getDbValue(db, "hourly_deleted_dates")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .plus(date)
                .distinct()
            put(db, "hourly_deleted_dates", deleted.joinToString(","))
        }
        recalculateAttendanceFromHourly()
    }

    /**
     * Monthly summary rows for the Hour Wise Attendance screen, newest month first.
     * A month with real per-day records is computed from those records; a month with
     * none yet falls back to the seeded totals so the screen still shows something
     * sensible before the admin has published data for that month.
     */
    fun hourlyMonthlySummary(): List<HourlyMonthSummary> {
        val allDates = hourlyDates()
        val byMonth: Map<String, List<String>> = allDates.groupBy { it.substring(0, 7) }

        val monthKeys = byMonth.keys
            .filter { it >= HOURLY_START_DATE.substring(0, 7) }
            .toSortedSet(compareByDescending { it })

        return monthKeys.map { key ->
            val datesInMonth = byMonth[key].orEmpty().sortedDescending()
            if (datesInMonth.isNotEmpty()) {
                var present = 0
                var absent = 0
                datesInMonth.forEach { date ->
                    hourlyHours(date).forEach { h ->
                        when (h) {
                            HourlyAttendance.PRESENT -> present++
                            HourlyAttendance.ABSENT -> absent++
                        }
                    }
                }
                HourlyMonthSummary(monthLabel(key), present, absent, datesInMonth)
            } else {
                val present = get("hourly_fallback_${key}_p").toIntOrNull() ?: 0
                val absent = get("hourly_fallback_${key}_a").toIntOrNull() ?: 0
                HourlyMonthSummary(monthLabel(key), present, absent, emptyList())
            }
        }
    }

    private fun monthLabel(yyyyMM: String): String {
        val parts = yyyyMM.split("-")
        if (parts.size != 2) return yyyyMM
        val year = parts[0]
        val monthNum = parts[1].toIntOrNull() ?: return yyyyMM
        val monthNames = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val name = monthNames.getOrNull(monthNum - 1) ?: return yyyyMM
        return "$name - $year"
    }

    // ---------------------------------------------------------------------
    // Last successful refresh/update timestamp
    // ---------------------------------------------------------------------

    fun lastUpdateTimestamp(): Long = get("last_update_epoch").toLongOrNull() ?: 0L

    fun setLastUpdateTimestamp(epochMillis: Long) {
        put("last_update_epoch", epochMillis.toString())
    }

    companion object {
        const val HOURLY_START_DATE = "2026-07-20"
        const val HOURLY_HISTORICAL_END_DATE = "2026-08-25"
    }
}