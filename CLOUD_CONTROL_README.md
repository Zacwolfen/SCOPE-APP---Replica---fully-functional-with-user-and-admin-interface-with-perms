# Cloud control extension

This version adds an authenticated cloud synchronization layer while preserving the existing local UI and LocalStore.

Added:
- Firebase Authentication
- Cloud Firestore
- optional Google Services plugin
- `CloudRepository`
- explicit Cloud Sync screen
- explicit Admin Control screen
- Firestore security rules
- Firebase setup documentation
- mobile-data capable synchronization

The normal app remains offline-first.

The cloud feature is intended for authorized devices/accounts controlled by the operator.
