package com.scopelocal.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scopelocal.app.data.LocalStore
import com.scopelocal.app.data.CloudRepository
import com.scopelocal.app.model.Attendance
import com.scopelocal.app.model.Course
import com.scopelocal.app.model.Exam
import com.scopelocal.app.model.HourlyAttendance
import com.scopelocal.app.model.Mark
import com.scopelocal.app.model.Student
import java.util.Locale
import java.util.Calendar
import android.app.DatePickerDialog

private val Blue = Color(0xFF0D73B8)
private val BrightBlue = Color(0xFF2196E5)
private val Red = Color(0xFFE45B63)
private val PageGray = Color(0xFFF7F7F7)
private val RowGray = Color(0xFFF2F2F2)
private val Border = Color(0xFFE0E0E0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(13, 115, 184)
        window.navigationBarColor = android.graphics.Color.WHITE
        setContent { ScopeLocalApp() }
    }
}

@Composable
fun ScopeLocalApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val cloud = remember { CloudRepository(context) }
    var syncRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        store.ensureHistoricalHourlyAttendance()
    }

    DisposableEffect(cloud, store) {
        var registration: com.google.firebase.firestore.ListenerRegistration? = null

        if (cloud.currentUserEmail != null) {
            val studentId = store.cloudStudentId().trim()

            if (studentId.isNotBlank()) {
                registration = cloud.listenStudent(studentId, store) {
                    syncRevision++
                }
            }
        }

        onDispose {
            registration?.remove()
        }
    }
    val nav = rememberNavController()

    var drawer by remember { mutableStateOf(false) }
    var demoSettings by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BrightBlue,
            background = Color.White
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(drawer) {
                    detectHorizontalDragGestures { _, drag ->
                        if (drag > 45) drawer = true
                        if (drag < -45) drawer = false
                    }
                }
        ) {
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("home") {
                    HomeScreen(
                        store = store,
                        cloud = cloud,
                        onMenu = { drawer = true },
                        onOpen = { nav.navigate(it) },
                        onSecretSettings = { demoSettings = true }
                    )
                }

                composable("profile") {
                    DetailShell("Profile", store, { nav.popBackStack() }) {
                        ProfileScreen(store)
                    }
                }

                composable("fee_due") {
                    DetailShell("Fee Dues", store, { nav.popBackStack() }) {
                        FeeDuesScreen()
                    }
                }

                composable("fee_paid") {
                    DetailShell("Fee Paid", store, { nav.popBackStack() }) {
                        FeePaidScreen()
                    }
                }

                composable("courses") {
                    DetailShell("Courses", store, { nav.popBackStack() }) {
                        CoursesScreen()
                    }
                }

                composable("completion") {
                    DetailShell("Completion Status", store, { nav.popBackStack() }) {
                        CompletionScreen()
                    }
                }

                composable("attendance") {
                    DetailShell("Attendance", store, { nav.popBackStack() }) {
                        AttendanceScreen(store, syncRevision)
                    }
                }

                composable("cumulative") {
                    DetailShell("Cumulative Attendance", store, { nav.popBackStack() }) {
                        CumulativeScreen(store)
                    }
                }

                composable("hour") {
                    DetailShell("Hour Attendance", store, { nav.popBackStack() }) {
                        HourAttendanceScreen(store, syncRevision)
                    }
                }

                composable("internal") {
                    DetailShell("Internal Marks", store, { nav.popBackStack() }) {
                        InternalMarksScreen(store)
                    }
                }

                composable("exam") {
                    DetailShell("Exam Details", store, { nav.popBackStack() }) {
                        ExamDetailsScreen(store)
                    }
                }

                composable("timetable") {
                    DetailShell("Timetable", store, { nav.popBackStack() }) {
                        TimetableScreen()
                    }
                }

                composable("provisional") {
                    DetailShell("Provisional Result", store, { nav.popBackStack() }) {
                        UnavailableScreen("Provisional Result Not Available.")
                    }
                }

                composable("revaluation") {
                    DetailShell("Revaluation / Review / Re-Totaling", store, { nav.popBackStack() }) {
                        UnavailableScreen("Revaluation / Review / Re-Totaling Results Not Available.")
                    }
                }

                composable("hostel") {
                    DetailShell("Hostel", store, { nav.popBackStack() }) {
                        UnavailableScreen("Hostel Data Not Available.")
                    }
                }

                composable("transport") {
                    DetailShell("Transport", store, { nav.popBackStack() }) {
                        UnavailableScreen("Transport Data Not Available.")
                    }
                }

                composable("notifications") {
                    DetailShell("Notification List", store, { nav.popBackStack() }) {
                        UnavailableScreen("Notifications Data Not Available.")
                    }
                }

                composable("cloud_user") {
                    DetailShell("Cloud Sync", store, { nav.popBackStack() }) {
                        CloudUserScreen(store, cloud)
                    }
                }

                composable("cloud_admin") {
                    DetailShell("Admin Control", store, { nav.popBackStack() }) {
                        CloudAdminScreen(store, cloud)
                    }
                }

                composable("attendance_editor") {
                    DetailShell("Attendance Editor", store, { nav.popBackStack() }) {
                        AttendanceEditorScreen(store)
                    }
                }

                composable("photo_editor") {
                    DetailShell("Profile Photo", store, { nav.popBackStack() }) {
                        PhotoEditorScreen(store)
                    }
                }
            }

            if (drawer) {
                DrawerOverlay(
                    onClose = { drawer = false },
                    onOpen = { route ->
                        drawer = false
                        nav.navigate(route)
                    }
                )
            }

            if (demoSettings) {
                DemoSettingsDialog(
                    onDismiss = { demoSettings = false },
                    onAttendance = {
                        demoSettings = false
                        nav.navigate("attendance_editor")
                    },
                    onPhoto = {
                        demoSettings = false
                        nav.navigate("photo_editor")
                    },
                    onCloudUser = {
                        demoSettings = false
                        nav.navigate("cloud_user")
                    },
                    onCloudAdmin = {
                        demoSettings = false
                        nav.navigate("cloud_admin")
                    }
                )
            }
        }
    }
}

@Composable
fun PortalTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onLongPressTitle: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Blue),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(60.dp)
                .fillMaxHeight()
                .clickable(enabled = onBack != null || onMenu != null) {
                    (onBack ?: onMenu)?.invoke()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (onBack != null) Icons.Default.ArrowBack else Icons.Default.Menu,
                null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            title,
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .pointerInput(onLongPressTitle) {
                    if (onLongPressTitle != null) {
                        detectTapGestures(
                            onLongPress = {
                                onLongPressTitle.invoke()
                            }
                        )
                    }
                }
        )

        if (onBack != null) {
            Icon(
                Icons.Default.History,
                null,
                tint = Color.White,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(26.dp)
            )
        } else {
            Box(
                Modifier
                    .padding(end = 18.dp)
                    .size(26.dp)
                    .clickable(enabled = onRefresh != null && !refreshing) { onRefresh?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DetailShell(title: String, store: LocalStore, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        PortalTopBar(title, onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        Footer(formatLastUpdate(store.lastUpdateTimestamp()))
    }
}

@Composable fun Footer(lastUpdateText: String) {
    Box(Modifier.fillMaxWidth().height(52.dp).background(Red), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Last Update", color = Color.White, fontSize = 11.sp)
            Text(lastUpdateText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Formats the persisted last-successful-refresh epoch into "dd-MMM-yyyy hh:mm a", e.g. 26-Aug-2026 12:42 AM. */
private fun formatLastUpdate(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Never"
    val sdf = java.text.SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.US)
    return sdf.format(java.util.Date(epochMillis))
}

data class Feature(val title: String, val route: String, val icon: ImageVector)

private val features = listOf(
    Feature("Profile", "profile", Icons.Default.Badge), Feature("Fee Dues", "fee_due", Icons.Default.CurrencyRupee), Feature("Fee Paid", "fee_paid", Icons.Default.Payments),
    Feature("Course List", "courses", Icons.Default.Groups), Feature("Course Status", "completion", Icons.Default.GridView), Feature("Timetable", "timetable", Icons.Default.CalendarMonth),
    Feature("Attendance", "attendance", Icons.Default.AssignmentInd), Feature("Cumulative\nAttendance", "cumulative", Icons.Default.MedicalInformation), Feature("Hour Wise\nAttendance", "hour", Icons.Default.Badge),
    Feature("Internal\nMarks", "internal", Icons.Default.CreditScore), Feature("Exam\nDetails", "exam", Icons.Default.CollectionsBookmark), Feature("Provisional\nResult", "provisional", Icons.Default.Description),
    Feature("Revaluation /\nReview / Re-", "revaluation", Icons.Default.Description), Feature("Hostel", "hostel", Icons.Default.Bed), Feature("Transport", "transport", Icons.Default.DirectionsBus),
    Feature("Notification\nList", "notifications", Icons.Default.Notifications), Feature("Logout", "home", Icons.Default.Logout)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    store: LocalStore,
    cloud: CloudRepository,
    onMenu: () -> Unit,
    onOpen: (String) -> Unit,
    onSecretSettings: () -> Unit
) {
    var refreshing by remember { mutableStateOf(false) }
    var lastUpdateMillis by remember { mutableStateOf(store.lastUpdateTimestamp()) }
    var refreshError by remember { mutableStateOf("") }

    fun runRefresh() {
        if (refreshing) return
        refreshing = true
        refreshError = ""

        val studentId = store.cloudStudentId()
        if (cloud.currentUserEmail == null || studentId.isBlank()) {
            // No cloud account signed in on this device: nothing to sync, but the local
            // view is current, so this still counts as a successful refresh.
            val now = System.currentTimeMillis()
            store.setLastUpdateTimestamp(now)
            lastUpdateMillis = now
            refreshing = false
            return
        }

        cloud.refreshStudentOnce(studentId, store) { ok, message ->
            if (ok) {
                val now = System.currentTimeMillis()
                store.setLastUpdateTimestamp(now)
                lastUpdateMillis = now
            } else {
                // Do not fake success: keep the previous timestamp and surface the error.
                refreshError = message
            }
            refreshing = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        PortalTopBar(
            title = "Student Portal",
            onMenu = onMenu,
            onLongPressTitle = onSecretSettings,
            onRefresh = { runRefresh() },
            refreshing = refreshing
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(features) { feature ->
                FeatureCard(feature) {
                    if (feature.route == "home") {
                        onMenu()
                    } else {
                        onOpen(feature.route)
                    }
                }
            }
        }

        if (refreshError.isNotBlank()) {
            Text(
                refreshError,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        Footer(formatLastUpdate(lastUpdateMillis))
    }
}

@Composable
fun FeatureCard(feature: Feature, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(138.dp).shadow(2.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F7F7)).clickable { onClick() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(BrightBlue), contentAlignment = Alignment.Center) {
            Icon(feature.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(feature.title, fontSize = 13.5.sp, textAlign = TextAlign.Center, lineHeight = 16.sp, maxLines = 2)
    }
}

@Composable
fun DrawerOverlay(onClose: () -> Unit, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxSize().background(Color(0x66000000))) {
        Column(Modifier.fillMaxWidth(0.92f).fillMaxHeight().shadow(8.dp).background(Color.White).padding(horizontal = 18.dp)) {
            PortalTopBar("Search", onBack = onClose)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(value = "", onValueChange = {}, placeholder = { Text("Search", color = BrightBlue) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = BrightBlue) }, singleLine = true, shape = RoundedCornerShape(50), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Black, focusedBorderColor = Color.Black), modifier = Modifier.fillMaxWidth().height(56.dp))
            Spacer(Modifier.height(10.dp))
            LazyColumn { items(features.filter { it.route != "home" }) { f ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(f.route) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(BrightBlue).border(1.5.dp, Color.White, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(f.icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(16.dp)); Text(f.title.replace("\n", " "), fontSize = 19.sp)
                }
            } }
        }
        Spacer(Modifier.weight(1f).clickable { onClose() })
    }
}

@Composable
fun ProfileScreen(store: LocalStore) {
    var student by remember { mutableStateOf(store.student()) }
    val imageContext = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color.White)
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        Box(
            Modifier
                .size(180.dp, 236.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(16.dp))
                .background(BrightBlue)
                .padding(5.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!student.photoUri.isNullOrBlank()) {
                val bitmap = remember(student.photoUri) {
                    try {
                        val uri = Uri.parse(student.photoUri)
                        imageContext.contentResolver
                            .openInputStream(uri)
                            ?.use { BitmapFactory.decodeStream(it) }
                    } catch (_: Exception) {
                        null
                    }
                }

                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(100.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(100.dp)
                )
            }
        }

        Spacer(Modifier.height(26.dp))

        ProfileField(Icons.Default.Person, "Name", student.name)
        ProfileField(Icons.Default.Transgender, "Gender", student.gender)
        ProfileField(Icons.Default.Cake, "Date of Birth", student.dob)
        ProfileField(Icons.Default.Badge, "Register No", student.registerNo)
        ProfileField(Icons.Default.AccountBalance, "Institution", student.institution)
        ProfileField(Icons.Default.School, "Program", student.program)
        ProfileField(Icons.Default.Class, "Semester", student.semester)
        ProfileField(Icons.Default.Groups, "Section", student.section)
        ProfileField(Icons.Default.LocationOn, "Campus", student.campus)
        ProfileField(Icons.Default.Person, "Parent's Name", student.parentName)
        ProfileField(Icons.Default.Event, "Date of Admission", student.admissionDate)
        ProfileField(Icons.Default.Star, "CGPA", student.cgpa)
        ProfileField(Icons.Default.StarBorder, "SGPA", student.sgpa)

        Spacer(Modifier.height(30.dp))
    }
}


@Composable
fun DemoSettingsDialog(
    onDismiss: () -> Unit,
    onAttendance: () -> Unit,
    onPhoto: () -> Unit,
    onCloudUser: () -> Unit,
    onCloudAdmin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Local Demo Settings")
        },
        text = {
            Column {
                Text(
                    "Local-only controls",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onAttendance,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.AssignmentInd,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Attendance")
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = onPhoto,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Change Profile Photo")
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = onCloudUser,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.CloudSync,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Cloud Sync")
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onCloudAdmin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.AdminPanelSettings,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Admin Control")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}


@Composable
fun CloudUserScreen(
    store: LocalStore,
    cloud: CloudRepository
) {
    var email by remember { mutableStateOf(cloud.currentUserEmail ?: store.get("savedUserEmail")) }
    var password by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf(store.cloudStudentId()) }
    var status by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(cloud.currentUserEmail != null) }

    LaunchedEffect(Unit) {
        if (cloud.currentUserEmail != null) {
            status = "Restoring session..."

            cloud.restoreUserSession(studentId) { ok, message ->
                signedIn = ok
                status = message
            }
        }
    }

    DisposableEffect(signedIn) {
        var registration: com.google.firebase.firestore.ListenerRegistration? = null

        if (signedIn) {
            registration = cloud.listenStudent(studentId.trim(), store) {
                status = it
            }
        }

        onDispose {
            registration?.remove()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Cloud Sync",
            color = BrightBlue,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "The existing local app remains usable offline. Cloud synchronization is optional.",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = studentId,
            onValueChange = { studentId = it },
            label = { Text("Student ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                store.setCloudStudentId(studentId)
                store.put("savedUserEmail", email.trim())
                status = "Signing in..."
                cloud.signInUser(email, password, studentId) { ok, message ->
                    signedIn = ok
                    status = message
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (signedIn) "CONNECTED" else "CONNECT")
        }

        if (signedIn) {
            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    status = "Signing out..."
                    cloud.signOut()
                    signedIn = false
                    status = "Signed out."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SIGN OUT")
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            status,
            color = if (status.contains("error", true) || status.contains("failed", true))
                MaterialTheme.colorScheme.error
            else
                BrightBlue,
            fontSize = 14.sp
        )
    }
}

@Composable
fun CloudAdminScreen(
    store: LocalStore,
    cloud: CloudRepository
) {
    var email by remember { mutableStateOf(cloud.currentUserEmail ?: store.get("savedAdminEmail")) }
    var password by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf(store.cloudStudentId()) }
    var status by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(cloud.currentUserEmail != null) }

    LaunchedEffect(Unit) {
        if (cloud.currentUserEmail != null) {
            email = cloud.currentUserEmail ?: email
            status = "Restoring saved admin session..."
            cloud.restoreAdminSession { ok, message ->
                signedIn = ok
                status = message
            }
        }
    }
    var rows by remember { mutableStateOf(store.attendance()) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    if (!signedIn) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                "Admin Control",
                color = BrightBlue,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Authorized administrator access. Firebase Authentication and admin authorization are required.",
                color = Color.Gray,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = studentId,
                onValueChange = { studentId = it },
                label = { Text("Student ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Admin Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    store.setCloudStudentId(studentId)
                    store.put("savedAdminEmail", email.trim())
                    status = "Signing in..."
                    cloud.signInAdmin(email, password) { ok, message ->
                        signedIn = ok
                        status = message
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ADMIN LOGIN")
            }

            Spacer(Modifier.height(18.dp))

            Text(
                status,
                color = if (status.contains("error", true) || status.contains("failed", true) || status.contains("not"))
                    MaterialTheme.colorScheme.error
                else
                    BrightBlue,
                fontSize = 14.sp
            )
        }
        return
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        item {
            Text(
                "Admin: $studentId",
                color = BrightBlue,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp)
            )

            Text(
                "Attendance Control",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    status = "Publishing current local data..."
                    cloud.uploadLocalStudent(studentId, store) { _, message ->
                        status = message
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("PUBLISH LOCAL DATA")
            }

            OutlinedButton(
                onClick = {
                    cloud.signOut()
                    signedIn = false
                    status = "Signed out."
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text("SIGN OUT")
            }

            Text(
                status,
                color = BrightBlue,
                fontSize = 13.sp,
                modifier = Modifier.padding(8.dp)
            )

            Spacer(Modifier.height(12.dp))
            HourlyAdminSection(
                studentId = studentId,
                store = store,
                cloud = cloud,
                onStatus = { status = it },
                onAttendanceChanged = { rows = store.attendance() }
            )
            Spacer(Modifier.height(4.dp))

            Text(
                "Attendance Control",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        itemsIndexed(rows) { i, a ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .shadow(1.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (i % 2 == 0) Color.White else RowGray)
                    .padding(14.dp)
            ) {
                Text(a.code, color = BrightBlue, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(a.name, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Present ${a.attended}/${a.conducted}",
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        String.format(Locale.US, "%.2f%%", a.percentage),
                        color = BrightBlue,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(onClick = { editIndex = i }) {
                        Text("EDIT")
                    }
                }
            }
        }
    }

    editIndex?.let { index ->
        AttendanceEditDialog(
            a = rows[index],
            onDismiss = { editIndex = null }
        ) { conducted, attended ->
            store.updateAttendance(index, conducted, attended)
            rows = store.attendance()

            cloud.updateAttendance(
                studentId = studentId,
                index = index,
                attendance = rows[index],
                store = store
            ) { _, message ->
                status = message
            }

            editIndex = null
        }
    }
}

/**
 * Admin hourly-attendance editor: add a class date, then tap any hour to change its
 * state (Present / Absent / No Class). Saves locally and pushes to Firestore at
 * students/{studentId}/hourlyAttendance/{date}; the student's real-time listener
 * (CloudRepository.listenStudent) picks the change up automatically.
 */
@Composable
fun HourlyAdminSection(
    studentId: String,
    store: LocalStore,
    cloud: CloudRepository,
    onStatus: (String) -> Unit,
    onAttendanceChanged: () -> Unit = {}
) {
    var dates by remember { mutableStateOf(store.hourlyDates()) }
    var editing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var deleteDate by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
    val context = LocalContext.current



    fun openDatePicker() {
        val min = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 20, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val initial = Calendar.getInstance().apply {
            if (time.before(min.time)) time = min.time
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply { set(year, month, day) }
                val date = dateFormatter.format(picked.time)
                if (date < LocalStore.HOURLY_START_DATE) {
                    onStatus("Hourly attendance starts from 20-Jul-2026.")
                } else {
                    selectedDate = date
                if (!dates.contains(date)) {
                    store.setHourlyHours(date, List(HourlyAttendance.PERIODS_PER_DAY) { HourlyAttendance.NO_CLASS })
                    cloud.updateHourlyAttendance(studentId, date, store.hourlyHours(date), store) { ok, message ->
                        onStatus(message)
                        if (ok) dates = store.hourlyDates()
                    }
                }
                    dates = store.hourlyDates()
                }
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = min.timeInMillis
        }.show()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text("Hourly Attendance Control", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrightBlue)
        Text(
            "Select a date from the calendar, then edit each period below.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { openDatePicker() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.DateRange, null)
            Spacer(Modifier.width(8.dp))
            Text("ADD / SELECT DATE")
        }

        selectedDate?.let { date ->
            Text(
                "Selected: ${hourlyDisplayDate(date)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = BrightBlue,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (dates.isEmpty()) {
            Text("No hourly attendance dates yet.", fontSize = 12.sp, color = Color.Gray)
        }

        dates.forEach { date ->
            val hours = store.hourlyHours(date)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = if (selectedDate == date) Color(0xFFEAF5FD) else Color.White)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            hourlyDisplayDate(date),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = BrightBlue,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { selectedDate = date }) {
                            Text("EDIT DAY")
                        }
                        IconButton(onClick = { deleteDate = date }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete attendance date", tint = Color(0xFFD32F2F))
                        }
                    }

                    Row(Modifier.fillMaxWidth()) {
                        hours.forEachIndexed { idx, h ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .padding(2.dp)
                                    .border(0.5.dp, Border)
                                    .clickable { editing = date to idx }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("H${idx + 1}", fontSize = 9.sp, color = Color.Gray)
                                    Text(
                                        h,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = when (h) {
                                            HourlyAttendance.PRESENT -> Color(0xFF2E7D32)
                                            HourlyAttendance.ABSENT -> Color(0xFFD32F2F)
                                            else -> Color.Gray
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { (date, idx) ->
        HourEditDialog(
            hourNumber = idx + 1,
            current = store.hourlyHours(date).getOrElse(idx) { HourlyAttendance.NO_CLASS },
            onDismiss = { editing = null },
            onSave = { newState ->
                store.setHourlyHour(date, idx, newState)
                val updatedHours = store.hourlyHours(date)
                cloud.updateHourlyAttendance(studentId, date, updatedHours, store) { _, message ->
                    onStatus(message)
                }
                dates = store.hourlyDates()
                onAttendanceChanged()
                editing = null
            }
        )
    }

    deleteDate?.let { date ->
        AlertDialog(
            onDismissRequest = { deleteDate = null },
            title = { Text("Delete Attendance?") },
            text = {
                Text("Are you sure you want to delete attendance for ${hourlyDisplayDate(date)}? This removes the entire day's hourly record.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.deleteHourlyDate(date)
                        cloud.deleteHourlyAttendance(studentId, date, store) { ok, message ->
                            onStatus(message)
                            if (ok) {
                                dates = store.hourlyDates()
                                onAttendanceChanged()
                                if (selectedDate == date) selectedDate = null
                            }
                        }
                        deleteDate = null
                    }
                ) {
                    Text("DELETE", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDate = null }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
fun HourEditDialog(
    hourNumber: Int,
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hour $hourNumber") },
        text = {
            Column {
                listOf(
                    HourlyAttendance.PRESENT to "Present",
                    HourlyAttendance.ABSENT to "Absent",
                    HourlyAttendance.NO_CLASS to "No Class"
                ).forEach { (code, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = code }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == code, onClick = { selected = code })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
fun AttendanceEditorScreen(store: LocalStore) {
    var rows by remember { mutableStateOf(store.attendance()) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        item {
            Text(
                "Local Attendance Editor",
                color = BrightBlue,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    start = 8.dp,
                    top = 18.dp,
                    bottom = 6.dp
                )
            )

            Text(
                "Changes are stored only on this device.",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(
                    start = 8.dp,
                    bottom = 14.dp
                )
            )
        }

        itemsIndexed(rows) { i, a ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .shadow(1.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (i % 2 == 0) Color.White else RowGray)
                    .padding(14.dp)
            ) {
                Text(
                    a.code,
                    color = BrightBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    a.name,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Present ${a.attended}/${a.conducted}",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        String.format(Locale.US, "%.2f%%", a.percentage),
                        color = BrightBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { editIndex = i }
                    ) {
                        Text("EDIT")
                    }
                }
            }
        }
    }

    editIndex?.let { index ->
        AttendanceEditDialog(
            a = rows[index],
            onDismiss = { editIndex = null }
        ) { conducted, attended ->
            store.updateAttendance(index, conducted, attended)
            rows = store.attendance()
            editIndex = null
        }
    }
}

@Composable
fun PhotoEditorScreen(store: LocalStore) {
    val context = LocalContext.current
    var student by remember { mutableStateOf(store.student()) }
    var message by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val destination = File(context.filesDir, "profile_photo.jpg")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }

                store.put(
                    "photoUri",
                    Uri.fromFile(destination).toString()
                )

                student = store.student()
                message = "Profile photo updated."
            } catch (_: Exception) {
                message = "Unable to update the profile photo."
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Local Profile Photo",
            color = BrightBlue,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Choose an image stored on this device.",
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Box(
            Modifier
                .size(190.dp, 245.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BrightBlue)
                .padding(5.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!student.photoUri.isNullOrBlank()) {
                val bitmap = remember(student.photoUri) {
                    try {
                        context.contentResolver
                            .openInputStream(Uri.parse(student.photoUri))
                            ?.use { BitmapFactory.decodeStream(it) }
                    } catch (_: Exception) {
                        null
                    }
                }

                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(80.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = { picker.launch("image/*") }
        ) {
            Icon(
                Icons.Default.Photo,
                contentDescription = null
            )

            Spacer(Modifier.width(8.dp))

            Text("Choose Photo")
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                color = BrightBlue,
                fontSize = 14.sp
            )
        }
    }
}

@Composable fun ProfileField(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = BrightBlue, modifier = Modifier.size(34.dp).padding(top = 3.dp))
        Spacer(Modifier.width(28.dp))
        Column { Text(label, color = BrightBlue, fontSize = 17.sp); Text(value, fontSize = 18.sp, lineHeight = 25.sp) }
    }
}

private data class FeeItem(val date: String, val desc: String, val amount: Double)
private val feeDues = listOf(
    "2025-2026" to listOf(FeeItem("02-Apr-2026", "Exam Fees", 2950.0)),
    "2024-2025" to listOf(FeeItem("06-Sep-2024", "Registration Fee", 10000.0), FeeItem("06-Sep-2024", "Tuition Fees", 350000.0))
)
private data class Receipt(val date: String, val receiptNo: String, val desc: String, val amount: Double)
private val feePaidReceipts = listOf(
    Receipt("11-11-2025", "2605", "Being Fee received from student for Exam Fees fee paid through online using Pay U with bank transaction id IGATMDTAW1 on 2025-11-09 20:45:17.31", 5100.0),
    Receipt("07-05-2025", "891", "Being Fee received from student for Exam Fees fee paid through online using Pay U with bank transaction id IGASTYXMG3 on 2025-05-04 22:15:05.05", 3300.0),
    Receipt("20-11-2024", "3110", "Being Fee received from student for Exam Fees fee paid through online using Pay U with bank transaction id IGASCEANK3 on 2024-11-17 12:31:02.532", 4050.0)
)

@Composable
fun FeeDuesScreen() {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(Modifier.fillMaxSize()) {
        feeDues.forEach { (year, items) ->
            val total = items.sumOf { it.amount }
            item {
                Row(Modifier.fillMaxWidth().clickable { expanded[year] = !(expanded[year] ?: true) }.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(year, color = BrightBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.US, "%.2f", total), color = BrightBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(if (expanded[year] ?: true) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = BrightBlue)
                }
                Divider(color = Border)
            }
            if (expanded[year] ?: true) {
                items.forEachIndexed { i, f ->
                    item {
                        Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) RowGray else Color.White).padding(horizontal = 18.dp, vertical = 14.dp)) {
                            Text(f.date, fontSize = 15.sp, modifier = Modifier.width(120.dp))
                            Text(f.desc, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(String.format(Locale.US, "%.2f", f.amount), fontSize = 15.sp)
                        }
                        Divider(color = Border)
                    }
                }
            }
        }
    }
}

@Composable
fun FeePaidScreen() {
    val total = feePaidReceipts.sumOf { it.amount }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("Receipt", color = BrightBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(String.format(Locale.US, "%.2f", total), color = BrightBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp)); Icon(Icons.Default.KeyboardArrowUp, null, tint = BrightBlue)
        }
        Divider(color = Border)
        LazyColumn {
            itemsIndexed(feePaidReceipts) { i, r ->
                Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) RowGray else Color.White).padding(horizontal = 12.dp, vertical = 14.dp)) {
                    Text(r.date, fontSize = 13.sp, modifier = Modifier.width(64.dp))
                    Text(r.receiptNo, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                    Text(r.desc, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                    Text(String.format(Locale.US, "%.2f", r.amount), fontSize = 13.sp)
                }
                Divider(color = Border)
            }
        }
    }
}

private val coursesBySem = mapOf(
    "Semester: 1" to listOf(Course("21LEH104T", "GERMAN", 1), Course("21MAB101T", "CALCULUS AND LINEAR ALGEBRA", 4), Course("21CYB101J", "CHEMISTRY", 3), Course("21BTB102T", "INTRODUCTION TO COMPUTATIONAL BIOLOGY", 3), Course("21CSS101J", "PROGRAMMING FOR PROBLEM SOLVING", 4), Course("21GNH101J", "PHILOSOPHY OF ENGINEERING", 2), Course("21MES101L", "BASIC CIVIL AND MECHANICAL WORKSHOP", 2), Course("21PDM101L", "PROFESSIONAL SKILLS AND PRACTICES", 2), Course("21GNM101L", "PHYSICAL AND MENTAL HEALTH USING YOGA", 0)),
    "Semester: 2" to listOf(Course("21CYM101T", "ENVIRONMENTAL SCIENCE", 2), Course("21MES102L", "ENGINEERING GRAPHICS AND DESIGN", 4), Course("21LEH101T", "COMMUNICATIVE ENGLISH", 2), Course("21MAB102T", "ADVANCED CALCULUS AND COMPLEX ANALYSIS", 4), Course("21PYB102J", "SEMICONDUCTOR PHYSICS AND COMPUTATIONAL METHODS", 3), Course("21EES101T", "ELECTRICAL AND ELECTRONICS ENGINEERING", 4), Course("21CSC101T", "OBJECT ORIENTED DESIGN AND PROGRAMMING", 4)),
    "Semester: 3" to listOf(Course("21MAB201T", "TRANSFORMS AND BOUNDARY VALUE PROBLEMS", 4), Course("21CSC201J", "DATA STRUCTURES AND ALGORITHMS", 4), Course("21CSC202J", "OPERATING SYSTEMS", 4), Course("21CSS201T", "COMPUTER ORGANIZATION AND ARCHITECTURE", 4), Course("21CSC203P", "ADVANCED PROGRAMMING PRACTICE", 2), Course("21DCS201P", "DESIGN THINKING AND METHODOLOGY", 2), Course("21LEM201T", "PROFESSIONAL ETHICS", 2)),
    "Semester: 4" to listOf(Course("21MAB204T", "PROBABILITY AND QUEUEING THEORY", 4), Course("21CSC204J", "DESIGN AND ANALYSIS OF ALGORITHMS", 4), Course("21CSC205P", "DATABASE MANAGEMENT SYSTEMS", 4), Course("21CSC206T", "ARTIFICIAL INTELLIGENCE", 4), Course("21CSE253T", "INTERNET OF THINGS", 4), Course("21PDH209T", "SOCIAL ENGINEERING", 2), Course("21LEM202T", "UNIVERSAL HUMAN VALUES - II", 2))
)

@Composable
fun CoursesScreen() {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(Modifier.fillMaxSize()) { coursesBySem.entries.forEach { (sem, courses) ->
        item { SemesterHeader(sem, expanded[sem] ?: true) { expanded[sem] = !(expanded[sem] ?: true) } }
        if (expanded[sem] != false) {
            item { TableHeader(listOf("Code", "Subject Description", "Credits"), listOf(0.25f, 0.58f, 0.17f)) }
            items(courses) { c -> TableRow(listOf(c.code, c.description, c.credits.toString()), listOf(0.25f, 0.58f, 0.17f)) }
        }
    } }
}

@Composable fun SemesterHeader(title: String, expanded: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, color = BrightBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.Black) } }
@Composable fun TableHeader(labels: List<String>, weights: List<Float>) { Row(Modifier.fillMaxWidth().background(Red).padding(vertical = 12.dp)) { labels.forEachIndexed { i, s -> Text(s, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(weights[i]).padding(horizontal = 4.dp)) } } }
@Composable fun TableRow(values: List<String>, weights: List<Float>) { Row(Modifier.fillMaxWidth().background(if (values[0].hashCode() % 2 == 0) RowGray else Color.White).padding(vertical = 11.dp)) { values.forEachIndexed { i, s -> Text(s, fontSize = 13.sp, modifier = Modifier.weight(weights[i]).padding(horizontal = 4.dp), textAlign = if (i == 1) TextAlign.Start else TextAlign.Center) } } }
@Composable fun GridTableHeader(labels: List<String>, weights: List<Float>) { Row(Modifier.fillMaxWidth().background(Red).border(0.5.dp, Border)) { labels.forEachIndexed { i, s -> Text(s, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(weights[i]).border(0.5.dp, Color.White.copy(alpha = 0.3f)).padding(horizontal = 6.dp, vertical = 12.dp)) } } }
@Composable fun GridTableRow(values: List<String>, weights: List<Float>, aligns: List<TextAlign> = emptyList(), shaded: Boolean = false) { Row(Modifier.fillMaxWidth().background(if (shaded) RowGray else Color.White).border(0.5.dp, Border)) { values.forEachIndexed { i, s -> Text(s, fontSize = 13.sp, modifier = Modifier.weight(weights[i]).border(0.5.dp, Border).padding(horizontal = 6.dp, vertical = 12.dp), textAlign = aligns.getOrElse(i) { TextAlign.Start }) } } }

@Composable fun CompletionScreen() {
    val rows = listOf(listOf("Basic Science Courses (BS)", "32", "32"), listOf("Engineering Science Courses (ES)", "31", "31"), listOf("Humanities and Social Sciences (HSS)", "12", "12"), listOf("Open Elective Courses (OE)", "9", "4"), listOf("Professional Core (PC)", "72", "60"), listOf("Professional Elective (PE)", "18", "6"), listOf("Project / Seminar", "8", "0"))
    val weights = listOf(1.6f, 1f, 1f)
    Column(Modifier.fillMaxSize()) {
        GridTableHeader(listOf("Description", "Required Credits", "Credits Acquired"), weights)
        LazyColumn { items(rows) { r -> GridTableRow(r, weights, listOf(TextAlign.Start, TextAlign.Start, TextAlign.Start)) } }
    }
}

@Composable
fun AttendanceScreen(store: LocalStore, syncRevision: Int = 0) {
    val rows = remember(syncRevision) { store.attendance() }

    val overallConducted = rows.sumOf { it.conducted }
    val overallPresent = rows.sumOf { it.attended }
    val overall =
        if (overallConducted == 0) 0.0
        else overallPresent * 100.0 / overallConducted

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Red)
                .padding(vertical = 12.dp)
        ) {
            listOf("Code", "TH", "PH", "AH", "%").forEachIndexed { i, h ->
                Text(
                    h,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(if (i == 0) 2.2f else 1f)
                )
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(rows) { i, a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (i % 2 == 0) Color.White else RowGray)
                        .padding(vertical = 15.dp)
                ) {
                    Text(
                        a.code,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(2.2f),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        a.conducted.toString(),
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        a.attended.toString(),
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        a.absent.toString(),
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        String.format(Locale.US, "%.2f %%", a.percentage),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Spacer(Modifier.height(14.dp))

                Text(
                    "Overall Attendance: ${
                        String.format(Locale.US, "%.2f%%", overall)
                    }",
                    color = BrightBlue,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )

                Spacer(Modifier.height(10.dp))
                LegendAttendance()

                Column(
                    Modifier.padding(
                        horizontal = 18.dp,
                        vertical = 4.dp
                    )
                ) {
                    rows.forEach { a ->
                        Text(
                            buildAnnotatedCodeName(a.code, a.name),
                            fontSize = 15.sp,
                            lineHeight = 24.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable fun LegendAttendance() { Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) { Text("LEGEND:", color = BrightBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("TH - Total Hours", fontSize = 15.sp); Text("PH - Present Hours", fontSize = 15.sp); Text("AH - Absent Hours", fontSize = 15.sp) } }

@Composable
fun buildAnnotatedCodeName(code: String, name: String) = androidx.compose.ui.text.buildAnnotatedString {
    androidx.compose.ui.text.SpanStyle(color = BrightBlue).let { style -> pushStyle(style); append(code); pop() }
    append(" - $name")
}

@Composable fun AttendanceEditDialog(a: Attendance, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) { var c by remember { mutableStateOf(a.conducted.toString()) }; var p by remember { mutableStateOf(a.attended.toString()) }; var error by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit Attendance") }, text = { Column { Text(a.name, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); OutlinedTextField(c, { c = it.filter(Char::isDigit) }, label = { Text("Total Hours") }); Spacer(Modifier.height(8.dp)); OutlinedTextField(p, { p = it.filter(Char::isDigit) }, label = { Text("Present Hours") }); if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error) } }, confirmButton = { TextButton(onClick = { val cv = c.toIntOrNull() ?: -1; val pv = p.toIntOrNull() ?: -1; if (cv < 0 || pv < 0 || pv > cv) error = "Present must be between 0 and Total Hours" else onSave(cv, pv) }) { Text("SAVE") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }) }

@Composable fun CumulativeScreen(store: LocalStore) {
    val weights = listOf(1.4f, .8f, .7f, .7f, .7f, .7f)
    Column(Modifier.fillMaxSize()) {
        GridTableHeader(listOf("Month/Year", "Pre.", "Abs", "ODP", "ODA", "Med"), weights)
        listOf("Jan-2026" to 101, "Feb-2026" to 125, "Mar-2026" to 127, "Apr-2026" to 33).forEachIndexed { i, m ->
            GridTableRow(listOf(m.first, m.second.toString(), "0", "0", "0", "0"), weights, listOf(TextAlign.Start, TextAlign.Center, TextAlign.Center, TextAlign.Center, TextAlign.Center, TextAlign.Center), shaded = i % 2 == 1)
        }
        Column(Modifier.padding(18.dp)) {
            Text("LEGEND:", color = BrightBlue, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("Pre - Present\nAbs - Absent\nODP - OD Present\nODA - OD Absent\nMed - Medical Leave", fontSize = 15.sp, lineHeight = 24.sp)
        }
    }
}

/**
 * Now reads real, admin-editable, Firestore-synchronized records from [LocalStore]
 * instead of a hard-coded month/mark list. See LocalStore.hourlyMonthlySummary().
 */
@Composable
fun HourAttendanceScreen(store: LocalStore, syncRevision: Int = 0) {
    val months = remember(syncRevision) { store.hourlyMonthlySummary() }
    val open = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn {
        months.forEach { month ->
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open[month.label] = !(open[month.label] ?: false) }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(month.label, color = BrightBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AssistChip(onClick = {}, label = { Text("P - ${month.present}") }, colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF4CAF50), labelColor = Color.White))
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, label = { Text("A - ${month.absent}") }, colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF44336), labelColor = Color.White))
                    Icon(if (open[month.label] == true) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                }
            }
            if (open[month.label] == true) {
                item { HourTable(store, month.dates) }
            }
        }
    }
}

@Composable
fun HourTable(store: LocalStore, dates: List<String>) {
    GridTableHeader(listOf("Date", "1", "2", "3", "4", "5", "6", "7"), listOf(1.5f, .5f, .5f, .5f, .5f, .5f, .5f, .5f))
    if (dates.isEmpty()) {
        Row(Modifier.fillMaxWidth().border(0.5.dp, Border).padding(14.dp)) {
            Text("No daily records published for this month yet.", fontSize = 13.sp, color = Color.Gray)
        }
        return
    }
    dates.forEachIndexed { i, date ->
        val marks = store.hourlyHours(date)
        Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) Color.White else RowGray).border(0.5.dp, Border)) {
            Text(hourlyDisplayDate(date), fontSize = 12.sp, modifier = Modifier.weight(1.5f).border(0.5.dp, Border).padding(6.dp))
            marks.forEach { mk ->
                Text(mk, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = when (mk) { "P" -> Color(0xFF2E7D32); "A" -> Color(0xFFD32F2F); else -> Color.Gray }, modifier = Modifier.weight(.5f).border(0.5.dp, Border).padding(6.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

/** "2026-04-08" -> "08-Apr-2026", matching the table's original display format. */
private fun hourlyDisplayDate(isoDate: String): String {
    return try {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)
        if (parsed != null) java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(parsed) else isoDate
    } catch (_: Exception) {
        isoDate
    }
}

@Composable
fun InternalMarksScreen(store: LocalStore) {
    val marks = remember { store.marks() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(BrightBlue)
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Code",
                        modifier = Modifier.weight(1.2f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Text(
                        "Subject",
                        modifier = Modifier.weight(3f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Text(
                        "Marks",
                        modifier = Modifier.weight(1.2f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(marks.size) { i ->
                val mark = marks[i]

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (i % 2 == 0) Color.White else RowGray
                        )
                        .border(0.5.dp, Border)
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        mark.code,
                        modifier = Modifier.weight(1.2f),
                        fontSize = 12.sp
                    )

                    Text(
                        mark.description,
                        modifier = Modifier
                            .weight(3f)
                            .padding(horizontal = 6.dp),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Text(
                        "${mark.acquired}/${mark.maximum}",
                        modifier = Modifier.weight(1.2f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
private val examMonths = mapOf("Semester: 1" to "DEC 2024", "Semester: 2" to "MAY 2025", "Semester: 3" to "NOV 2025", "Semester: 4" to "MAY 2026")
private val staticExams = mapOf(
    "Semester: 1" to listOf(Exam("21LEH104T", "GERMAN", "A", "Pass"), Exam("21MAB101T", "CALCULUS AND LINEAR ALGEBRA", "B+", "Pass"), Exam("21CYB101J", "CHEMISTRY", "B+", "Pass"), Exam("21BTB102T", "INTRODUCTION TO COMPUTATIONAL BIOLOGY", "A", "Pass"), Exam("21CSS101J", "PROGRAMMING FOR PROBLEM SOLVING", "A", "Pass"), Exam("21GNH101J", "PHILOSOPHY OF ENGINEERING", "A", "Pass"), Exam("21MES101L", "BASIC CIVIL AND MECHANICAL WORKSHOP", "O", "Pass"), Exam("21PDM101L", "PROFESSIONAL SKILLS AND PRACTICES", "O", "Pass"), Exam("21GNM101L", "PHYSICAL AND MENTAL HEALTH USING YOGA", "O", "Pass")),
    "Semester: 2" to listOf(Exam("21LEM101T", "CONSTITUTION OF INDIA", "O", "Pass"), Exam("21MAB102T", "ADVANCED CALCULUS AND COMPLEX ANALYSIS", "A", "Pass"), Exam("21PYB102J", "SEMICONDUCTOR PHYSICS AND COMPUTATIONAL METHODS", "A", "Pass"), Exam("21EES101T", "ELECTRICAL AND ELECTRONICS ENGINEERING", "A+", "Pass"), Exam("21CSC101T", "OBJECT ORIENTED DESIGN AND PROGRAMMING", "A", "Pass"), Exam("21CYM101T", "ENVIRONMENTAL SCIENCE", "O", "Pass"), Exam("21MES102L", "ENGINEERING GRAPHICS AND DESIGN", "A+", "Pass"), Exam("21LEH101T", "COMMUNICATIVE ENGLISH", "A+", "Pass")),
    "Semester: 3" to listOf(Exam("21MAB201T", "TRANSFORMS AND BOUNDARY VALUE PROBLEMS", "A", "Pass"), Exam("21CSC201J", "DATA STRUCTURES AND ALGORITHMS", "B+", "Pass"), Exam("21CSC202J", "OPERATING SYSTEMS", "A", "Pass"), Exam("21CSS201T", "COMPUTER ORGANIZATION AND ARCHITECTURE", "A+", "Pass"), Exam("21CSC203P", "ADVANCED PROGRAMMING PRACTICE", "A+", "Pass"), Exam("21DCS201P", "DESIGN THINKING AND METHODOLOGY", "O", "Pass"), Exam("21LEM201T", "PROFESSIONAL ETHICS", "A+", "Pass"))
)

@Composable
fun ExamDetailsScreen(store: LocalStore) {
    val exams = remember { store.exams() }

    val semesters = listOf(
        "Semester: 1" to staticExams.getValue("Semester: 1"),
        "Semester: 2" to staticExams.getValue("Semester: 2"),
        "Semester: 3" to staticExams.getValue("Semester: 3"),
        "Semester: 4" to exams
    )

    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val weights = listOf(1f, 2f, 0.65f, 0.65f)

    LazyColumn {
        semesters.forEach { (s, list) ->
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            expanded[s] = !(expanded[s] ?: (s == "Semester: 4"))
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s,
                        color = BrightBlue,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        examMonths[s] ?: "",
                        color = BrightBlue,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.width(6.dp))

                    Icon(
                        if (expanded[s] ?: (s == "Semester: 4"))
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                        null,
                        tint = Color.Black
                    )
                }

                Divider(color = Border)
            }

            if ((expanded[s] ?: (s == "Semester: 4")) && list.isNotEmpty()) {
                item {
                    GridTableHeader(
                        listOf("Code", "Description", "Grade", "Result"),
                        weights
                    )
                }

                itemsIndexed(list) { i, e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (i % 2 == 0) Color.White else RowGray)
                            .border(0.5.dp, Border)
                    ) {
                        Text(
                            e.code,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .weight(weights[0])
                                .border(0.5.dp, Border)
                                .padding(6.dp)
                        )

                        Text(
                            e.description,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .weight(weights[1])
                                .border(0.5.dp, Border)
                                .padding(6.dp)
                        )

                        Text(
                            e.grade,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .weight(weights[2])
                                .border(0.5.dp, Border)
                                .padding(6.dp),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            e.result,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .weight(weights[3])
                                .border(0.5.dp, Border)
                                .padding(6.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private data class TTCell(val subject: String, val room: String)
private data class TimetableDay(
    val day: String,
    val periods: List<String>
)



private val timetableTimes = listOf(
    "8:00-8:50",
    "8:50-9:40",
    "9:40-10:30",
    "10:30-10:50",
    "10:50-11:40",
    "11:40-12:30",
    "12:30-1:15",
    "1:15-2:00",
    "2:00-2:50",
    "2:50-3:40"
)

private val timetableDays = listOf(
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday"
)

/*
 * Timetable layout:
 * - 8:00-8:50 and 8:50-9:40 are included.
 * - 10:30-10:50 is the fixed break.
 * - 12:30-1:15 is the fixed lunch period.
 * - Subject abbreviations follow the requested names:
 *   FLAT -> FLA
 *   FSWD -> PE-2
 *   IoD -> OE-1
 */
private val timetableSubjects = listOf(
    listOf("CN", "OE-1", "FLA", "BREAK", "DM", "CC", "LUNCH", "CN LAB", "CN LAB", "-"),
    listOf("DM", "FLA", "ML", "BREAK", "CN LAB", "CN LAB", "LUNCH", "PE-2", "-", "-"),
    listOf("FLA", "ML LAB", "ML LAB", "BREAK", "CN", "IAF", "LUNCH", "OE-1", "-", "-"),
    listOf("PE-2", "CN", "ML", "BREAK", "OE-1", "FLA", "LUNCH", "CN", "DM", "-"),
    listOf("OE-1", "FLA", "DM", "BREAK", "PE-2", "PE-2", "LUNCH", "LIB", "-", "-")
)

private val timetableSubjectDetails = listOf(
    Triple("21MAB302T", "Discrete Mathematics for Engineers", "Dr. F. Ragupathi"),
    Triple("21CSC301T", "Formal Language and Automata", "Ms. S. Niveditha"),
    Triple("21CSC302J", "Computer Networks", "Dr. M. Daniel Nareshkumar"),
    Triple("21CSC305P", "Machine Learning", "Ms. A. Nivashini"),
    Triple("21LEM301T", "Indian Art Form", "Dr. S. Arulya"),
    Triple("21MEO115T", "Introduction to Drones", "Dr. A. Visagan"),
    Triple("21GNP301L", "Community Connect", "Dr. S. Sridevi"),
    Triple("21CSE354T", "Full Stack Web Development", "Ms. M. Indumathy"),
    Triple("21CSE359T", "Information Storage and Management", "Dr. P. Durgadevi"),
    Triple("21IPE312P", "ERP Solution for Digital Enterprises", "Dr. R. Gayathri"),
    Triple("-", "Library", "Ms. S. Niveditha")
)

@Composable
fun TimetableScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Text(
            text = "Timetable",
            color = BrightBlue,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                start = 20.dp,
                top = 18.dp,
                bottom = 4.dp
            )
        )

        Text(
            text = "V SEMESTER",
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = Modifier.padding(
                start = 20.dp,
                bottom = 14.dp
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                bottom = 24.dp
            )
        ) {
            item {
                HorizontalScrollableTimetable()
            }

            item {
                Spacer(Modifier.height(22.dp))

                Text(
                    text = "SUBJECT DETAILS",
                    color = BrightBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        start = 8.dp,
                        bottom = 10.dp
                    )
                )

                timetableSubjectDetails.forEach { (code, name, teacher) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 7.dp),
                        color = Color(0xFFF7F7F7),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(11.dp)
                        ) {
                            Text(
                                text = code,
                                color = BrightBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = name,
                                color = Color.Black,
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            )
                            Text(
                                text = teacher,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalScrollableTimetable() {
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScroll)
    ) {
        Column(
            modifier = Modifier.width(1080.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
            ) {
                TimetableHeaderCell("DAY", 110.dp)

                timetableTimes.forEach { time ->
                    TimetableHeaderCell(time, 97.dp)
                }
            }

            timetableDays.forEachIndexed { dayIndex, day ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                ) {
                    TimetableDayCell(day)

                    timetableSubjects[dayIndex].forEach { subject ->
                        TimetableSubjectCell(subject)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableHeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(0.7.dp, Color.White)
            .background(BrightBlue),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun TimetableDayCell(day: String) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .fillMaxHeight()
            .border(0.7.dp, Border)
            .background(Color(0xFFF1F1F1)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day,
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimetableSubjectCell(subject: String) {
    val special = subject == "BREAK" || subject == "LUNCH"

    Box(
        modifier = Modifier
            .width(97.dp)
            .fillMaxHeight()
            .border(0.7.dp, Border)
            .background(
                when {
                    subject == "BREAK" -> Color(0xFFE8E8E8)
                    subject == "LUNCH" -> Color(0xFFE8E8E8)
                    subject == "-" -> Color.White
                    else -> Color(0xFFF8FBFE)
                }
            )
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = subject,
            color = if (subject == "-") Color.Gray else Color.Black,
            fontSize = 11.sp,
            fontWeight = if (special) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}

@Composable fun UnavailableScreen(message: String) { Box(Modifier.fillMaxSize().padding(top = 260.dp, start = 22.dp, end = 22.dp)) { Text(message, fontSize = 18.sp, textAlign = TextAlign.Start) } }
@Composable fun TablePage(headers:List<String>, rows:List<List<String>>){Column(Modifier.fillMaxSize()){TableHeader(headers,headers.map{1f/headers.size});LazyColumn{items(rows){r->TableRow(r,headers.map{1f/headers.size})}}}}