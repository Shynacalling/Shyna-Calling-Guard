const express = require('express');
const { AccessToken } = require('livekit-server-sdk');
const admin = require('firebase-admin');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 10000;

// Initialize Firebase Admin if environment variable is present
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

app.get('/health', (req, res) => {
  res.json({ ok: true });
});

app.post('/token', async (req, res) => {
  const { roomName, participantName } = req.body;

  if (!roomName) {
    return res.status(400).json({ error: 'roomName is required' });
  }

  const apiKey = process.env.LIVEKIT_API_KEY;
  const apiSecret = process.env.LIVEKIT_API_SECRET;

  if (!apiKey || !apiSecret) {
    return res.status(500).json({ error: 'LiveKit credentials not configured on server' });
  }

  try {
    const at = new AccessToken(apiKey, apiSecret, {
      identity: participantName || `user-${Math.floor(Math.random() * 10000)}`,
      ttl: '1h',
    });

    at.addGrant({
      roomJoin: true,
      room: roomName,
      canPublish: true,
      canSubscribe: true,
    });

    const token = await at.toJwt();
    res.json({
      token,
      roomName,
      identity: at.identity
    });
  } catch (err) {
    console.error('Error generating token:', err);
    res.status(500).json({ error: 'Failed to generate token' });
  }
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server listening on port ${PORT}`);
});
