import express from "express";
import admin from "firebase-admin";

// ==========================================
// 1. Firebase Admin Initialization
// ==========================================
function initFirebase() {
  const serviceAccountEnv = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!serviceAccountEnv) {
    console.error("FATAL: FIREBASE_SERVICE_ACCOUNT_JSON environment variable is not set.");
    process.exit(1);
  }

  let serviceAccount;
  try {
    // Try parsing as raw JSON string first
    serviceAccount = JSON.parse(serviceAccountEnv);
  } catch (_err) {
    try {
      // If parsing fails, try Base64 decoding then parsing
      const decoded = Buffer.from(serviceAccountEnv, "base64").toString("utf-8");
      serviceAccount = JSON.parse(decoded);
    } catch (e) {
      console.error("FATAL: Could not parse FIREBASE_SERVICE_ACCOUNT_JSON as JSON or Base64-encoded JSON:", e.message);
      process.exit(1);
    }
  }

  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
  });

  console.log("Firebase Admin successfully initialized for project:", serviceAccount.project_id || "default");
}

initFirebase();
const db = admin.firestore();
const messaging = admin.messaging();

const app = express();
app.use(express.json());

// ==========================================
// 2. Authentication & Rate Limiting
// ==========================================
const RELAY_API_KEY = process.env.RELAY_API_KEY || "";
if (!RELAY_API_KEY) {
  console.warn("WARNING: RELAY_API_KEY environment variable is not set. All authenticated requests will fail!");
}

/**
 * Security Model Note:
 * This service uses a shared API secret (X-Relay-Key) between the Sekai Tune Android client
 * and this relay service to prevent arbitrary third parties from triggering notifications.
 * While a static key embedded in the APK is not a replacement for full per-user OAuth tokens,
 * combined with server-side per-target rate limiting, it provides sufficient protection for
 * this personal-scale deployment.
 */
function requireRelayKey(req, res, next) {
  const clientKey = req.headers["x-relay-key"];
  if (!RELAY_API_KEY || !clientKey || clientKey !== RELAY_API_KEY) {
    return res.status(401).json({ error: "Unauthorized: Invalid or missing X-Relay-Key" });
  }
  next();
}

// In-memory rate limiting: max 10 requests per targetUid per 60 seconds
const rateLimitMap = new Map(); // targetUid -> [timestamps]

function checkRateLimit(targetUid) {
  const now = Date.now();
  const windowMs = 60 * 1000;
  const maxRequests = 10;

  const timestamps = (rateLimitMap.get(targetUid) || []).filter(t => now - t < windowMs);
  if (timestamps.length >= maxRequests) {
    return false;
  }
  timestamps.push(now);
  rateLimitMap.set(targetUid, timestamps);
  return true;
}

// Cleanup rate limit map every 10 minutes
setInterval(() => {
  const now = Date.now();
  for (const [uid, timestamps] of rateLimitMap.entries()) {
    const valid = timestamps.filter(t => now - t < 60 * 1000);
    if (valid.length === 0) {
      rateLimitMap.delete(uid);
    } else {
      rateLimitMap.set(uid, valid);
    }
  }
}, 10 * 60 * 1000);

// ==========================================
// 3. Helper: Fetch Tokens & Dispatch FCM
// ==========================================
async function sendNotificationToUser(targetUid, { title, body, data = {} }) {
  console.log(`[Relay] Fetching FCM tokens from users/${targetUid}/fcmTokens...`);
  const tokensSnapshot = await db
    .collection("users")
    .doc(targetUid)
    .collection("fcmTokens")
    .get();

  if (tokensSnapshot.empty) {
    console.log(`[Relay] No registered FCM tokens found for targetUid: ${targetUid}`);
    return { sentCount: 0, removedCount: 0, warning: "No tokens found for user" };
  }

  const tokenDocs = tokensSnapshot.docs;
  console.log(`[Relay] Found ${tokenDocs.length} FCM token(s) for targetUid: ${targetUid}`);
  tokenDocs.forEach((doc, idx) => {
    const rawToken = doc.data().token || "";
    const truncated = rawToken.length > 14
      ? `${rawToken.substring(0, 7)}...${rawToken.substring(rawToken.length - 7)}`
      : rawToken;
    console.log(`[Relay]   Token #${idx + 1} (docId: ${doc.id}): ${truncated}`);
  });

  const messages = tokenDocs.map(doc => {
    const token = doc.data().token;
    return {
      token,
      // DATA-ONLY payload: No top-level notification object.
      // This ensures message routing is always delivered directly to BuddyMessagingService.onMessageReceived()
      // in both foreground and background, preventing duplicate OS notifications while the app is active.
      data: {
        title,
        body,
        ...data,
      },
      android: {
        priority: "high",
      },
    };
  });

  console.log(`[Relay] Dispatching data-only FCM payload via messaging.sendEach (${messages.length} message(s))...`);
  const response = await messaging.sendEach(messages);
  let sentCount = 0;
  const deadDocIds = [];

  response.responses.forEach((resp, idx) => {
    if (resp.success) {
      sentCount++;
      console.log(`[Relay]   Message #${idx + 1} sent successfully. MessageId: ${resp.messageId}`);
    } else {
      const errCode = resp.error?.code;
      const errMsg = resp.error?.message;
      console.warn(`[Relay]   Message #${idx + 1} FAILED (docId: ${tokenDocs[idx].id}): [${errCode}] ${errMsg}`);
      if (
        errCode === "messaging/registration-token-not-registered" ||
        errCode === "messaging/invalid-registration-token"
      ) {
        deadDocIds.push(tokenDocs[idx].id);
      }
    }
  });

  // Clean up stale tokens asynchronously
  if (deadDocIds.length > 0) {
    console.log(`[Relay] Cleaning up ${deadDocIds.length} invalid FCM tokens for user ${targetUid}`);
    const batch = db.batch();
    deadDocIds.forEach(id => {
      const ref = db.collection("users").doc(targetUid).collection("fcmTokens").doc(id);
      batch.delete(ref);
    });
    batch.commit().catch(err => console.error("[Relay] Error pruning dead tokens:", err));
  }

  return { sentCount, totalCount: messages.length, removedCount: deadDocIds.length };
}

// ==========================================
// 4. API Endpoints
// ==========================================

// Health check
app.get("/", (req, res) => {
  res.json({
    status: "ok",
    service: "sekai-tune-relay-service",
    timestamp: new Date().toISOString(),
  });
});

// Notify: New Buddy Request
app.post("/notify/buddy-request", requireRelayKey, async (req, res) => {
  try {
    const { targetUid, fromDisplayName } = req.body || {};
    if (!targetUid || typeof targetUid !== "string") {
      return res.status(400).json({ error: "Missing or invalid targetUid" });
    }

    if (!checkRateLimit(targetUid)) {
      return res.status(429).json({ error: "Too many notifications sent to this recipient. Please try again later." });
    }

    const senderName = fromDisplayName && typeof fromDisplayName === "string" ? fromDisplayName.trim() : null;
    const bodyText = senderName ? `${senderName} sent you a buddy request` : "You have a new buddy request";

    console.log(`[Relay] Sending buddy request push to targetUid: ${targetUid} from: ${senderName}`);
    const result = await sendNotificationToUser(targetUid, {
      title: "Sekai Tune",
      body: bodyText,
      data: {
        type: "buddy_request",
        fromDisplayName: senderName || "",
      },
    });

    res.json({ success: true, ...result });
  } catch (err) {
    console.error("[Relay] Error in /notify/buddy-request:", err);
    res.status(500).json({ error: "Internal server error", message: err.message });
  }
});

// Notify: Together Online Session Invite
app.post("/notify/invite", requireRelayKey, async (req, res) => {
  try {
    const { targetUid, hostDisplayName, sessionCode } = req.body || {};
    if (!targetUid || typeof targetUid !== "string") {
      return res.status(400).json({ error: "Missing or invalid targetUid" });
    }

    if (!checkRateLimit(targetUid)) {
      return res.status(429).json({ error: "Too many notifications sent to this recipient. Please try again later." });
    }

    const hostName = (hostDisplayName && typeof hostDisplayName === "string" && hostDisplayName.trim()) || "A buddy";
    const code = (sessionCode && typeof sessionCode === "string") ? sessionCode.trim() : "";

    console.log(`[Relay] Sending session invite push from '${hostName}' to targetUid: ${targetUid}`);
    const result = await sendNotificationToUser(targetUid, {
      title: "Together Online",
      body: `${hostName} invited you to a Together session`,
      data: {
        type: "together_invite",
        hostDisplayName: hostName,
        sessionCode: code,
      },
    });

    res.json({ success: true, ...result });
  } catch (err) {
    console.error("[Relay] Error in /notify/invite:", err);
    res.status(500).json({ error: "Internal server error", message: err.message });
  }
});

// Start Server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Sekai Tune Relay Service running on port ${PORT}`);
});
