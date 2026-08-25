const express = require('express');
const { AccessToken } = require('livekit-server-sdk');
const admin = require('firebase-admin');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 10000;

// Initialize Firebase Admin
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  try {
    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);

    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });

    console.log('Firebase Admin initialized');
  } catch (err) {
    console.error('Failed to initialize Firebase Admin:', err);
  }
}

// Middleware to verify Firebase ID Token
async function verifyToken(req, res, next) {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized' });
  }

  const idToken = authHeader.split('Bearer ')[1];

  try {
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    req.user = decodedToken;
    next();
  } catch (err) {
    console.error('Verify token failed:', err);

    return res.status(401).json({
      error: 'Unauthorized'
    });
  }
}

// Health check
app.get('/health', (req, res) => {
  res.json({ ok: true });
});

// LiveKit token endpoint
app.post('/token', async (req, res) => {
  const { roomName, participantName } = req.body;

  if (!roomName) {
    return res.status(400).json({
      error: 'roomName is required'
    });
  }

  const apiKey = process.env.LIVEKIT_API_KEY;
  const apiSecret = process.env.LIVEKIT_API_SECRET;

  if (!apiKey || !apiSecret) {
    return res.status(500).json({
      error: 'LiveKit credentials not configured on server'
    });
  }

  try {
    const at = new AccessToken(apiKey, apiSecret, {
      identity:
        participantName ||
        `user-${Math.floor(Math.random() * 10000)}`,
      ttl: '1h'
    });

    at.addGrant({
      roomJoin: true,
      room: roomName,
      canPublish: true,
      canSubscribe: true
    });

    const token = await at.toJwt();

    return res.json({
      token,
      roomName,
      identity: at.identity
    });
  } catch (err) {
    console.error('LiveKit token creation failed:', err);

    return res.status(500).json({
      error: 'Failed to create LiveKit token'
    });
  }
});

// Endpoint to trigger FCM notification for incoming calls
app.post('/notify-call', verifyToken, async (req, res) => {
  const {
    callId,
    receiverUid,
    callerName,
    callType
  } = req.body;

  if (!callId || !receiverUid || !callerName || !callType) {
    return res.status(400).json({
      error: 'Missing required fields'
    });
  }

  try {
    // 1. Get receiver's FCM token
    const userRef = admin
      .firestore()
      .collection('users')
      .doc(receiverUid);

    const userDoc = await userRef.get();

    if (!userDoc.exists) {
      return res.status(404).json({
        error: 'Receiver not found'
      });
    }

    const fcmToken = userDoc.data().fcmToken;

    if (!fcmToken) {
      return res.status(404).json({
        error: 'Receiver FCM token not found'
      });
    }

    // 2. Build high-priority FCM message
    const message = {
      data: {
        callId: String(callId),
        callerName: String(callerName),
        callType: String(callType),
        type: 'INCOMING_CALL'
      },

      token: fcmToken,

      android: {
        priority: 'high',
        ttl: 0
      }
    };

    // 3. Send FCM
    await admin.messaging().send(message);

    console.log(
      `FCM sent for call ${callId} to user ${receiverUid}`
    );

    return res.json({
      success: true
    });

  } catch (err) {

    // Old / invalid FCM token
    if (
      err.code === 'messaging/registration-token-not-registered' ||
      err.code === 'messaging/invalid-registration-token'
    ) {
      console.log(
        `Removing stale FCM token for user ${receiverUid}`
      );

      try {
        await admin
          .firestore()
          .collection('users')
          .doc(receiverUid)
          .update({
            fcmToken: admin.firestore.FieldValue.delete()
          });

        console.log(
          `Stale FCM token removed for user ${receiverUid}`
        );
      } catch (deleteErr) {
        console.error(
          'Failed to remove stale FCM token:',
          deleteErr
        );
      }

      return res.status(410).json({
        error: 'Receiver FCM token is no longer registered',
        code: 'STALE_FCM_TOKEN'
      });
    }

    console.error('Notify call failed:', err);

    return res.status(500).json({
      error: 'Internal server error'
    });
  }
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server listening on port ${PORT}`);
});