import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";

admin.initializeApp();
const db = admin.firestore();

/**
 * Helper function to send an FCM push notification to all registered
 * device tokens for a given user UID.
 */
async function sendNotificationToUser(
  uid: string,
  title: string,
  body: string,
  data?: Record<string, string>
): Promise<void> {
  try {
    const tokensSnapshot = await db
      .collection("users")
      .doc(uid)
      .collection("fcmTokens")
      .get();

    if (tokensSnapshot.empty) {
      return;
    }

    const tokens: string[] = [];
    tokensSnapshot.forEach((doc) => {
      const token = doc.data()?.token;
      if (token && typeof token === "string") {
        tokens.push(token);
      }
    });

    if (tokens.length === 0) {
      return;
    }

    const message: admin.messaging.MulticastMessage = {
      tokens,
      notification: {
        title,
        body,
      },
      data: data || {},
      android: {
        priority: "high",
        notification: {
          channelId: "buddy_notifications",
        },
      },
    };

    const response = await admin.messaging().sendEachForMulticast(message);
    console.log(
      `FCM multicast to ${uid}: ${response.successCount} succeeded, ${response.failureCount} failed.`
    );
  } catch (error) {
    console.error(`Error sending push notification to user ${uid}:`, error);
  }
}

/**
 * acceptBuddyRequest
 *
 * Accepts a pending buddy request:
 * 1. Validates caller authentication.
 * 2. Fetches the request doc and verifies status is "pending".
 * 3. Verifies caller is toUid.
 * 4. Atomically creates buddies/{fromUid}/list/{toUid} and
 *    buddies/{toUid}/list/{fromUid}, and deletes the request document.
 * 5. Sends an FCM notification to fromUid.
 */
export const acceptBuddyRequest = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError(
      "unauthenticated",
      "The function must be called while authenticated."
    );
  }

  const callerUid = request.auth.uid;
  const requestId = request.data?.requestId;
  const toDisplayName = request.data?.toDisplayName || "Your buddy";

  if (!requestId || typeof requestId !== "string") {
    throw new HttpsError(
      "invalid-argument",
      "The 'requestId' parameter is required and must be a string."
    );
  }

  const requestRef = db.collection("buddy_requests").doc(requestId);
  const requestSnap = await requestRef.get();

  if (!requestSnap.exists) {
    throw new HttpsError(
      "failed-precondition",
      "The buddy request does not exist."
    );
  }

  const requestData = requestSnap.data();
  if (!requestData || requestData.status !== "pending") {
    throw new HttpsError(
      "failed-precondition",
      "The buddy request is not pending."
    );
  }

  if (requestData.toUid !== callerUid) {
    throw new HttpsError(
      "permission-denied",
      "You are not authorized to accept this buddy request."
    );
  }

  const fromUid = requestData.fromUid;
  const fromDisplayName = requestData.fromDisplayName || "Buddy";

  const batch = db.batch();

  // Write buddies/{fromUid}/list/{toUid}
  const fromBuddyRef = db
    .collection("buddies")
    .doc(fromUid)
    .collection("list")
    .doc(callerUid);

  batch.set(fromBuddyRef, {
    uid: callerUid,
    displayName: toDisplayName,
    addedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Write buddies/{toUid}/list/{fromUid}
  const toBuddyRef = db
    .collection("buddies")
    .doc(callerUid)
    .collection("list")
    .doc(fromUid);

  batch.set(toBuddyRef, {
    uid: fromUid,
    displayName: fromDisplayName,
    addedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Delete the buddy_requests doc
  batch.delete(requestRef);

  await batch.commit();

  // Send FCM push to requester
  await sendNotificationToUser(
    fromUid,
    "Buddy Request Accepted",
    `${toDisplayName} accepted your buddy request.`,
    {
      type: "buddy_request_accepted",
      buddyUid: callerUid,
    }
  );

  return { success: true };
});

/**
 * rejectBuddyRequest
 *
 * Rejects a pending buddy request:
 * 1. Validates caller authentication.
 * 2. Fetches the request doc and verifies status is "pending".
 * 3. Verifies caller is toUid.
 * 4. Atomically deletes the buddy_requests document.
 * 5. Sends a neutral FCM notification to fromUid.
 */
export const rejectBuddyRequest = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError(
      "unauthenticated",
      "The function must be called while authenticated."
    );
  }

  const callerUid = request.auth.uid;
  const requestId = request.data?.requestId;

  if (!requestId || typeof requestId !== "string") {
    throw new HttpsError(
      "invalid-argument",
      "The 'requestId' parameter is required and must be a string."
    );
  }

  const requestRef = db.collection("buddy_requests").doc(requestId);
  const requestSnap = await requestRef.get();

  if (!requestSnap.exists) {
    throw new HttpsError(
      "failed-precondition",
      "The buddy request does not exist."
    );
  }

  const requestData = requestSnap.data();
  if (!requestData || requestData.status !== "pending") {
    throw new HttpsError(
      "failed-precondition",
      "The buddy request is not pending."
    );
  }

  if (requestData.toUid !== callerUid) {
    throw new HttpsError(
      "permission-denied",
      "You are not authorized to reject this buddy request."
    );
  }

  const fromUid = requestData.fromUid;

  // Delete the buddy_requests doc
  await requestRef.delete();

  // Send a neutral notification to the requester
  await sendNotificationToUser(
    fromUid,
    "Buddy Request Update",
    "Your buddy request wasn't accepted.",
    {
      type: "buddy_request_rejected",
    }
  );

  return { success: true };
});

/**
 * removeBuddy
 *
 * Removes a mutual buddy relationship:
 * 1. Validates caller authentication.
 * 2. In a single atomic batch, deletes buddies/{myUid}/list/{buddyUid} and
 *    buddies/{buddyUid}/list/{myUid}.
 * 3. No notification sent.
 */
export const removeBuddy = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError(
      "unauthenticated",
      "The function must be called while authenticated."
    );
  }

  const myUid = request.auth.uid;
  const buddyUid = request.data?.buddyUid;

  if (!buddyUid || typeof buddyUid !== "string") {
    throw new HttpsError(
      "invalid-argument",
      "The 'buddyUid' parameter is required and must be a string."
    );
  }

  const batch = db.batch();

  // Delete buddies/{myUid}/list/{buddyUid}
  const myBuddyRef = db
    .collection("buddies")
    .doc(myUid)
    .collection("list")
    .doc(buddyUid);

  batch.delete(myBuddyRef);

  // Delete buddies/{buddyUid}/list/{myUid}
  const otherBuddyRef = db
    .collection("buddies")
    .doc(buddyUid)
    .collection("list")
    .doc(myUid);

  batch.delete(otherBuddyRef);

  await batch.commit();

  return { success: true };
});
