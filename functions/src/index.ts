import * as admin from "firebase-admin";

admin.initializeApp();
// const db = admin.firestore();

// Note: Cloud Functions deployment requires the Firebase Blaze (pay-as-you-go) plan.
// Buddy list operations (accept/reject/remove) are currently handled via direct client-side
// Firestore atomic batch writes with secure Firestore rules.
