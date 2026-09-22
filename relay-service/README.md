# Sekai Tune FCM Push Notification Relay Service

A lightweight, self-hosted relay microservice designed to run on **Render's Free Tier** (or any Node.js host) to dispatch real Firebase Cloud Messaging (FCM) push notifications when buddy requests or Together Online session invites occur.

---

## Why this service exists
Firebase Spark (free plan) does not support Cloud Functions. Instead of leaving notifications to foreground-only Firestore snapshot listeners, this service receives an authenticated request from the sender and uses the Firebase Admin SDK to push an FCM HTTP v1 notification directly to the recipient's device tokens.

---

## Endpoints

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `GET` | `/` | Health check endpoint | No |
| `POST` | `/notify/buddy-request` | Sends "You have a new buddy request" push to `targetUid` | Yes (`X-Relay-Key`) |
| `POST` | `/notify/invite` | Sends Together session invite push to `targetUid` | Yes (`X-Relay-Key`) |

### Request Bodies:
- **POST `/notify/buddy-request`**:
  ```json
  {
    "targetUid": "TARGET_FIREBASE_USER_UID"
  }
  ```
- **POST `/notify/invite`**:
  ```json
  {
    "targetUid": "TARGET_FIREBASE_USER_UID",
    "hostDisplayName": "Alice",
    "sessionCode": "123456"
  }
  ```

---

## Security Model
1. **Shared API Secret (`X-Relay-Key`)**: All notification endpoints require the `X-Relay-Key` header matching the `RELAY_API_KEY` environment variable.
2. **In-Memory Rate Limiting**: Max 10 notification dispatches per target UID per 60 seconds to prevent abuse.
3. **Dead Token Pruning**: Automatically cleans up invalidated or unregistered FCM tokens from Firestore `users/{uid}/fcmTokens` to keep database reads efficient.

---

## Step-by-Step Render Deployment Guide

### Step 1: Obtain your Firebase Service Account JSON
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Click **Project Settings** (gear icon) > **Service accounts** tab.
3. Click **Generate new private key**, then click **Generate key**.
4. A `.json` file will download to your computer. Open it and copy its entire text contents.

### Step 2: Deploy on Render
1. Go to [Render Dashboard](https://dashboard.render.com/) (sign up for free if you haven't).
2. Click **New +** > **Web Service**.
3. Connect your GitHub repository (`sekai-tune`).
4. Configure the following settings:
   - **Name**: `sekai-tune-relay` (or any name you prefer)
   - **Region**: Closest to your users (e.g., Oregon, Frankfurt, Singapore)
   - **Root Directory**: `relay-service`  *(Important!)*
   - **Environment**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
   - **Plan Type**: `Free`

### Step 3: Set Environment Variables in Render
In the **Environment Variables** section on Render, add:
1. `RELAY_API_KEY`: Generate a random secure string (e.g. `sk_relay_9f8a7b6c5d4e3f2a1b0c`).
2. `FIREBASE_SERVICE_ACCOUNT_JSON`: Paste the entire raw JSON text from Step 1 (or its base64-encoded string).

Click **Deploy Web Service**.

### Step 4: Configure Android App
Once deployed, Render will give you an HTTPS URL (e.g. `https://sekai-tune-relay.onrender.com`).

Add these two lines to your `local.properties` file on your development machine:
```properties
RELAY_SERVICE_URL=https://sekai-tune-relay.onrender.com
RELAY_API_KEY=sk_relay_9f8a7b6c5d4e3f2a1b0c
```
Now build your Android app. The app will automatically route push notifications through your relay service!
