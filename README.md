
# ScopeLocal

Android application built with Kotlin and Jetpack Compose.

## Features

- Student dashboard
- Profile
- Attendance
- Timetable
- Course information
- Internal marks
- Exam details
- Local data persistence
- Automatic attendance progression
- Firebase Authentication
- Firebase Firestore synchronization
- Admin attendance control
- Cloud attendance updates
- Offline-first local data

## Cloud Architecture

Admin device → Firebase Firestore → Student device

The admin can remotely update attendance through Firebase. Student devices receive the changes through Firestore synchronization.

## Technology

- Kotlin
- Jetpack Compose
- Android SDK
- Firebase Authentication
- Firebase Firestore
- SQLite / local persistence

## Package

`com.scopelocal.app`

## Build

```bash
./gradlew clean :app:assembleDebug