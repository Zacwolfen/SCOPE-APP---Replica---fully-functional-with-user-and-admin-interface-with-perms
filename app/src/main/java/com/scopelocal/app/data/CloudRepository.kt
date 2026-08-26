package com.scopelocal.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.scopelocal.app.model.Attendance
import com.scopelocal.app.model.Exam
import com.scopelocal.app.model.HourlyAttendance
import com.scopelocal.app.model.Mark

class CloudRepository(context: Context) {

    private val app: FirebaseApp? = try {
        FirebaseApp.initializeApp(context)
    } catch (_: Exception) {
        null
    }

    private val auth: FirebaseAuth? = app?.let {
        try { FirebaseAuth.getInstance(it) } catch (_: Exception) { null }
    }

    private val db: FirebaseFirestore? = app?.let {
        try { FirebaseFirestore.getInstance(it) } catch (_: Exception) { null }
    }

    val isConfigured: Boolean
        get() = app != null && auth != null && db != null

    val currentUserEmail: String?
        get() = auth?.currentUser?.email

    fun signInAdmin(
        email: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val a = auth
        if (a == null) {
            onResult(false, "Firebase is not configured. Add app/google-services.json.")
            return
        }

        a.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                it.user?.getIdToken(true)
                    ?.addOnSuccessListener { token ->
                        val isAdmin = token.claims["admin"] == true
                        if (isAdmin) {
                            onResult(true, "Admin signed in.")
                        } else {
                            a.signOut()
                            onResult(false, "This account is not an authorized admin.")
                        }
                    }
                    ?.addOnFailureListener {
                        a.signOut()
                        onResult(false, "Unable to verify admin role.")
                    }
                    ?: run {
                        a.signOut()
                        onResult(false, "Unable to verify admin role.")
                    }
            }
            .addOnFailureListener {
                onResult(false, it.message ?: "Admin sign-in failed.")
            }
    }

    fun restoreAdminSession(
        onResult: (Boolean, String) -> Unit
    ) {
        val a = auth
        val user = a?.currentUser
        if (a == null || user == null) {
            onResult(false, "No saved admin session.")
            return
        }

        user.getIdToken(true)
            .addOnSuccessListener { token ->
                if (token.claims["admin"] == true) {
                    onResult(true, "Admin session restored.")
                } else {
                    a.signOut()
                    onResult(false, "Saved account is not an authorized admin.")
                }
            }
            .addOnFailureListener {
                onResult(false, it.message ?: "Unable to restore admin session.")
            }
    }

    fun signInUser(
        email: String,
        password: String,
        studentId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val a = auth
        val database = db

        if (a == null || database == null) {
            onResult(false, "Firebase is not configured. Add app/google-services.json.")
            return
        }

        a.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    a.signOut()
                    onResult(false, "User account is unavailable.")
                    return@addOnSuccessListener
                }

                database.collection("users").document(uid).get()
                    .addOnSuccessListener { snapshot ->
                        val allowedStudentId = snapshot.getString("studentId")
                        if (allowedStudentId == studentId.trim()) {
                            onResult(true, "User signed in.")
                        } else {
                            a.signOut()
                            onResult(false, "This account is not paired with that student.")
                        }
                    }
                    .addOnFailureListener {
                        a.signOut()
                        onResult(false, it.message ?: "Unable to verify student pairing.")
                    }
            }
            .addOnFailureListener {
                onResult(false, it.message ?: "User sign-in failed.")
            }
    }

    fun signOut() {
        auth?.signOut()
    }

    fun restoreUserSession(
    studentId: String,
    onResult: (Boolean, String) -> Unit
) {
    val a = auth
    val database = db
    val user = a?.currentUser

    if (a == null || database == null || user == null) {
        onResult(false, "No saved session.")
        return
    }

    database.collection("users")
        .document(user.uid)
        .get()
        .addOnSuccessListener { snapshot ->
            val allowedStudentId = snapshot.getString("studentId")

            if (allowedStudentId == studentId.trim()) {
                onResult(true, "Synced")
            } else {
                a.signOut()
                onResult(false, "Saved account is not paired with this student.")
            }
        }
        .addOnFailureListener {
            onResult(
                false,
                it.message ?: "Unable to restore cloud session."
            )
        }
}

    fun uploadLocalStudent(
        studentId: String,
        store: LocalStore,
        onResult: (Boolean, String) -> Unit
    ) {
        val database = db
        val user = auth?.currentUser

        if (database == null || user == null) {
            onResult(false, "Admin must be signed in and Firebase must be configured.")
            return
        }

        val student = store.student()
        store.recalculateAttendanceFromHourly()
        val attendance = store.attendance()
        val marks = store.marks()
        val exams = store.exams()

        val root = database.collection("students").document(studentId)

        val profile = hashMapOf<String, Any>(
            "name" to student.name,
            "gender" to student.gender,
            "dob" to student.dob,
            "registerNo" to student.registerNo,
            "institution" to student.institution,
            "program" to student.program,
            "semester" to student.semester,
            "batch" to student.batch,
            "section" to student.section,
            "campus" to student.campus,
            "parentName" to student.parentName,
            "admissionDate" to student.admissionDate,
            "cgpa" to student.cgpa,
            "sgpa" to student.sgpa,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val rootData = hashMapOf<String, Any>(
            "studentId" to studentId,
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to user.uid
        )

        root.set(rootData, SetOptions.merge())
            .continueWithTask {
                root.collection("profile").document("current").set(profile, SetOptions.merge())
            }
            .continueWithTask {
                val batch = database.batch()
                attendance.forEachIndexed { i, a ->
                    val (baseConducted, baseAttended) = store.attendanceBaseline(i)
                    val data = attendanceData(a, user.uid, baseConducted, baseAttended)
                    batch.set(
                        root.collection("attendance").document(i.toString()),
                        data,
                        SetOptions.merge()
                    )
                }
                batch.commit()
            }
            .continueWithTask {
                val batch = database.batch()
                marks.forEachIndexed { i, m ->
                    val data = hashMapOf<String, Any>(
                        "code" to m.code,
                        "description" to m.description,
                        "acquired" to m.acquired,
                        "maximum" to m.maximum,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(
                        root.collection("internalMarks").document(i.toString()),
                        data,
                        SetOptions.merge()
                    )
                }
                batch.commit()
            }
            .continueWithTask {
                val batch = database.batch()
                exams.forEachIndexed { i, e ->
                    val data = hashMapOf<String, Any>(
                        "code" to e.code,
                        "description" to e.description,
                        "grade" to e.grade,
                        "result" to e.result,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(
                        root.collection("exams").document(i.toString()),
                        data,
                        SetOptions.merge()
                    )
                }
                batch.commit()
            }
            .continueWithTask {
                val hourlyDates = store.hourlyDates()
                val batch = database.batch()
                hourlyDates.forEach { date ->
                    val hours = store.hourlyHours(date)
                    batch.set(
                        root.collection("hourlyAttendance").document(date),
                        hourlyAttendanceData(date, hours),
                        SetOptions.merge()
                    )
                }
                batch.commit()
            }
            .addOnSuccessListener {
                onResult(true, "Student data published to cloud.")
            }
            .addOnFailureListener {
                onResult(false, it.message ?: "Cloud upload failed.")
            }
    }

    private fun hourlyAttendanceData(date: String, hours: List<String>): HashMap<String, Any> {
        val data = hashMapOf<String, Any>(
            "date" to date,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        hours.forEachIndexed { i, h -> data["hour${i + 1}"] = h }
        return data
    }

    /**
     * Admin-facing: push a single date's hourly attendance to
     * students/{studentId}/hourlyAttendance/{date}. The student's real-time listener
     * (see [listenStudent]) picks this up automatically over the internet/mobile data,
     * no shared Wi-Fi required.
     */
    fun updateHourlyAttendance(
        studentId: String,
        date: String,
        hours: List<String>,
        store: LocalStore,
        onResult: (Boolean, String) -> Unit
    ) {
        val database = db
        val user = auth?.currentUser

        if (database == null || user == null) {
            onResult(false, "Admin is not signed in.")
            return
        }

        // Hourly attendance is the canonical period-level source. Recalculate the
        // regular attendance projection before publishing both datasets together.
        store.recalculateAttendanceFromHourly()
        val root = database.collection("students").document(studentId)
        val batch = database.batch()

        batch.set(
            root.collection("hourlyAttendance").document(date),
            hourlyAttendanceData(date, hours).apply { put("updatedBy", user.uid) },
            SetOptions.merge()
        )

        store.attendance().forEachIndexed { i, attendance ->
            val (baseConducted, baseAttended) = store.attendanceBaseline(i)
            batch.set(
                root.collection("attendance").document(i.toString()),
                attendanceData(attendance, user.uid, baseConducted, baseAttended),
                SetOptions.merge()
            )
        }

        batch.commit()
            .addOnSuccessListener { onResult(true, "Hourly + regular attendance updated.") }
            .addOnFailureListener { onResult(false, it.message ?: "Attendance update failed.") }
    }

    /** Admin-facing: permanently delete one day's hourly attendance document. */
    fun deleteHourlyAttendance(
        studentId: String,
        date: String,
        store: LocalStore,
        onResult: (Boolean, String) -> Unit
    ) {
        val database = db
        val user = auth?.currentUser
        if (database == null || user == null) {
            onResult(false, "Admin is not signed in.")
            return
        }

        store.recalculateAttendanceFromHourly()
        val root = database.collection("students").document(studentId)
        val batch = database.batch()
        batch.delete(root.collection("hourlyAttendance").document(date))

        store.attendance().forEachIndexed { i, attendance ->
            val (baseConducted, baseAttended) = store.attendanceBaseline(i)
            batch.set(
                root.collection("attendance").document(i.toString()),
                attendanceData(attendance, user.uid, baseConducted, baseAttended),
                SetOptions.merge()
            )
        }

        batch.commit()
            .addOnSuccessListener { onResult(true, "Attendance day deleted and totals updated.") }
            .addOnFailureListener { onResult(false, it.message ?: "Attendance deletion failed.") }
    }

    private fun attendanceData(
        attendance: Attendance,
        userId: String,
        baseConducted: Int,
        baseAttended: Int
    ): HashMap<String, Any> = hashMapOf(
        "code" to attendance.code,
        "name" to attendance.name,
        "faculty" to attendance.faculty,
        "conducted" to attendance.conducted,
        "attended" to attendance.attended,
        "percentage" to attendance.percentage,
        "baseConducted" to baseConducted,
        "baseAttended" to baseAttended,
        "updatedAt" to FieldValue.serverTimestamp(),
        "updatedBy" to userId
    )

    fun updateAttendance(
        studentId: String,
        index: Int,
        attendance: Attendance,
        store: LocalStore,
        onResult: (Boolean, String) -> Unit
    ) {
        val database = db
        val user = auth?.currentUser

        if (database == null || user == null) {
            onResult(false, "Admin is not signed in.")
            return
        }

        val (baseConducted, baseAttended) = store.attendanceBaseline(index)
        val data = attendanceData(attendance, user.uid, baseConducted, baseAttended)

        database.collection("students")
            .document(studentId)
            .collection("attendance")
            .document(index.toString())
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                onResult(true, "Attendance updated.")
            }
            .addOnFailureListener {
                onResult(false, it.message ?: "Attendance update failed.")
            }
    }

    fun listenStudent(
        studentId: String,
        store: LocalStore,
        onState: (String) -> Unit
    ): ListenerRegistration? {
        val database = db
        val user = auth?.currentUser

        if (database == null || user == null) {
            onState("Not connected")
            return null
        }

        val root = database.collection("students").document(studentId)

        val listeners = mutableListOf<ListenerRegistration>()

        listeners += root.collection("profile").document("current")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onState("Sync error")
                    return@addSnapshotListener
                }

                snapshot?.data?.let { data ->
                    val fields = listOf(
                        "name", "gender", "dob", "registerNo", "institution",
                        "program", "semester", "batch", "section", "campus",
                        "parentName", "admissionDate", "cgpa", "sgpa"
                    )

                    fields.forEach { key ->
                        data[key]?.toString()?.let { value ->
                            val localKey = when (key) {
                                "registerNo" -> "register"
                                else -> key
                            }
                            store.put(localKey, value)
                        }
                    }
                }
                onState("Synced")
            }

        listeners += root.collection("attendance")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    onState("Sync error")
                    return@addSnapshotListener
                }

                snapshots?.documents?.forEach { document ->
                    val i = document.id.toIntOrNull() ?: return@forEach
                    val data = document.data ?: return@forEach

                    data["baseConducted"]?.let { base ->
                        val baseAttended = numberString(data["baseAttended"] ?: 0).toIntOrNull() ?: 0
                        store.setAttendanceBaselineFromCloud(
                            i,
                            numberString(base).toIntOrNull() ?: 0,
                            baseAttended
                        )
                    }

                }

                store.recalculateAttendanceFromHourly()
                onState("Synced")
            }

        listeners += root.collection("internalMarks")
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener

                snapshots?.documents?.forEach { document ->
                    val i = document.id.toIntOrNull() ?: return@forEach
                    val data = document.data ?: return@forEach

                    data["acquired"]?.let {
                        store.put("mark_${i}_acq", numberString(it))
                    }
                }
            }

        listeners += root.collection("exams")
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener

                snapshots?.documents?.forEach { document ->
                    val i = document.id.toIntOrNull() ?: return@forEach
                    val data = document.data ?: return@forEach

                    data["grade"]?.toString()?.let {
                        store.put("exam_${i}_grade", it)
                    }
                    data["result"]?.toString()?.let {
                        store.put("exam_${i}_result", it)
                    }
                }
            }

        listeners += root.collection("hourlyAttendance")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    onState("Sync error")
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    when (change.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED ->
                            applyHourlyDocument(store, change.document.id, change.document.data)
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED ->
                            store.deleteHourlyDate(change.document.id)
                    }
                }

                store.recalculateAttendanceFromHourly()
                onState("Synced")
            }

        return object : ListenerRegistration {
            override fun remove() {
                listeners.forEach { it.remove() }
            }
        }
    }

    private fun applyHourlyDocument(store: LocalStore, date: String, data: Map<String, Any>?) {
        if (data == null) return
        val hours = (1..HourlyAttendance.PERIODS_PER_DAY).map { period ->
            data["hour$period"]?.toString() ?: HourlyAttendance.NO_CLASS
        }
        store.setHourlyHours(date, hours)
    }

    private fun applyProfileFields(store: LocalStore, data: Map<String, Any>) {
        val fields = listOf(
            "name", "gender", "dob", "registerNo", "institution",
            "program", "semester", "batch", "section", "campus",
            "parentName", "admissionDate", "cgpa", "sgpa"
        )
        fields.forEach { key ->
            data[key]?.toString()?.let { value ->
                val localKey = when (key) {
                    "registerNo" -> "register"
                    else -> key
                }
                store.put(localKey, value)
            }
        }
    }

    /**
     * One-time forced re-fetch of cloud data (used by the top-right refresh button), as
     * opposed to the always-on real-time listener above. Unlike a passive listener this
     * gives a definite success/failure callback, so the UI can update the "Last Update"
     * timestamp only on genuine success and surface an error (without touching the
     * previous timestamp) when there is no internet connection.
     */
    fun refreshStudentOnce(
        studentId: String,
        store: LocalStore,
        onResult: (Boolean, String) -> Unit
    ) {
        val database = db
        val user = auth?.currentUser

        if (database == null || user == null) {
            onResult(false, "Cloud sync is not signed in.")
            return
        }

        val root = database.collection("students").document(studentId)

        root.collection("profile").document("current").get()
            .continueWithTask { profileTask ->
                profileTask.result?.data?.let { data -> applyProfileFields(store, data) }
                root.collection("attendance").get()
            }
            .continueWithTask { attTask ->
                attTask.result?.documents?.forEach { document ->
                    val i = document.id.toIntOrNull()
                    val data = document.data
                    if (i != null && data != null) {
                        data["baseConducted"]?.let { base ->
                            val baseAttended = numberString(data["baseAttended"] ?: 0).toIntOrNull() ?: 0
                            store.setAttendanceBaselineFromCloud(
                                i,
                                numberString(base).toIntOrNull() ?: 0,
                                baseAttended
                            )
                        }
                    }
                }
                root.collection("hourlyAttendance").get()
            }
            .continueWithTask { hourTask ->
                val cloudDates = hourTask.result?.documents?.map { it.id }?.toSet().orEmpty()
                // Historical seeded dates are local bootstrap data until they are published.
                // Never wipe 20-Jul through 25-Aug merely because a refresh runs before the
                // admin has published those records. Future dates remain cloud-authoritative.
                store.hourlyDates()
                    .filter { it !in cloudDates && it > LocalStore.HOURLY_HISTORICAL_END_DATE }
                    .forEach { store.deleteHourlyDate(it) }
                hourTask.result?.documents?.forEach { document ->
                    applyHourlyDocument(store, document.id, document.data)
                }
                store.recalculateAttendanceFromHourly()
                root.collection("internalMarks").get()
            }
            .continueWithTask { markTask ->
                markTask.result?.documents?.forEach { document ->
                    val i = document.id.toIntOrNull()
                    val data = document.data
                    if (i != null && data != null) {
                        data["acquired"]?.let { store.put("mark_${i}_acq", numberString(it)) }
                    }
                }
                root.collection("exams").get()
            }
            .addOnSuccessListener { examSnap ->
                examSnap.documents.forEach { document ->
                    val i = document.id.toIntOrNull()
                    val data = document.data
                    if (i != null && data != null) {
                        data["grade"]?.toString()?.let { store.put("exam_${i}_grade", it) }
                        data["result"]?.toString()?.let { store.put("exam_${i}_result", it) }
                    }
                }
                onResult(true, "Refreshed")
            }
            .addOnFailureListener {
                onResult(false, it.message ?: "Refresh failed. Check your internet connection.")
            }
    }

    private fun numberString(value: Any): String {
        return when (value) {
            is Long -> value.toString()
            is Double -> {
                if (value % 1.0 == 0.0) value.toLong().toString()
                else value.toString()
            }
            is Number -> value.toString()
            else -> value.toString()
        }
    }
}
