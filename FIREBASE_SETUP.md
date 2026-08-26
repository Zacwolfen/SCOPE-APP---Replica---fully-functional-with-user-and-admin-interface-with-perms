# Firebase / mobile-data sync setup

The Android project is prepared for Firebase Authentication + Cloud Firestore.

The app remains usable offline when Firebase is not configured.

## 1. Create Firebase project

Create a Firebase project of your own.

Add an Android application using:

- package/application ID: `com.scopelocal.app`

Download `google-services.json`.

Place it at:

`app/google-services.json`

The project applies the Google Services Gradle plugin automatically only when that file exists.

## 2. Enable Authentication

In Firebase Console:

Authentication → Sign-in method → Email/Password → Enable.

Create two kinds of accounts:

- administrator
- user/student

## 3. Administrator authorization

The Android admin screen verifies the Firebase ID token claim:

`admin == true`

A custom claim must therefore be assigned to the administrator account using a trusted server/Admin SDK. Never put a Firebase service-account key in the Android application.

Example server-side Admin SDK operation:

`setCustomUserClaims(uid, { admin: true })`

After changing claims, sign out and sign back in so a fresh ID token is obtained.

## 4. User pairing

Create:

`users/{USER_UID}`

with:

```text
studentId: "DEMO001"
```

The user app asks for:

- Student ID
- Email
- Password

It only accepts the login if the authenticated UID's `users/{uid}.studentId` matches the entered student ID.

## 5. Student data

The initial student record is:

`students/DEMO001`

The admin screen has `PUBLISH LOCAL DATA`, which can upload the current local student data to this record.

## 6. Security rules

Deploy `firestore.rules`.

The rules intentionally prevent normal users from writing student records.

Administrators require the `admin` custom claim.

Do not use public rules such as:

`allow read, write: if true;`

## 7. Test on two phones

Phone A:
- install the same APK
- sign in using the authorized administrator account
- choose `DEMO001`
- publish/edit attendance

Phone B:
- install the same APK
- open Cloud Sync
- sign in using the paired user account
- use mobile data

The phones do not need to be on the same Wi-Fi network.

## 8. Offline behavior

Without internet:
- existing LocalStore data remains available
- normal screens continue working
- cloud operations show an error/status instead of blocking the app

When internet is restored:
- the user-side Firestore listeners can receive the latest cloud-controlled values.

## 9. Build

With `google-services.json` present:

```bash
./gradlew clean :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```
