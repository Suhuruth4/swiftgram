# Cove (Monorepo)

Cove is a private messaging app: end-to-end encryption, OTP login (email + phone), realtime WebSocket updates, encrypted attachments, and web push notifications.

> **Renamed from SwiftGram.** See [Migrating from SwiftGram](#migrating-from-swiftgram) if you have an existing dev environment.

## What's inside
- **server/** - Spring Boot backend (Java 21, package `com.cove`)
- **client/** - React + Vite web app
- **infra/** - Docker Compose: Postgres, Redis, NATS, MinIO, Mailpit (dev SMTP)

## Features (implemented)
- Email + phone OTP login (welcome email on email signup)
- E2EE for message bodies and attachments (client-side encryption)
- Direct + group chats, typing indicators, read receipts
- Message edits/deletes, reactions, replies, pinning
- Encrypted file uploads (images/audio/files) — photos and voice notes decrypt inline
- Web push notifications (VAPID)

## Quick start (dev)
1) **Start infrastructure**
```
docker compose -f infra/docker-compose.yml up -d
```

2) **Run backend**
```
cd server
./gradlew bootRun
```

3) **Run web client**
```
cd client
npm install
npm run dev
```

4) Open the app
```
http://localhost:5173
```

## Configuration
All config lives in `server/src/main/resources/application.yml` and can be overridden with environment variables.

### JWT (required)
Set a strong secret (32+ chars):
```
app.jwt.secret=your-very-long-secret
```

### OTP providers (dev defaults)
- Email: defaults to **Mailpit** (local SMTP). Open http://localhost:8025
- SMS: defaults to **mock** (OTP printed in server logs)

To enable Twilio SMS:
```
app.sms.provider=twilio
app.sms.twilio.accountSid=...
app.sms.twilio.authToken=...
app.sms.twilio.fromNumber=...
```

### Web push (optional)
Generate VAPID keys and set:
```
app.push.vapidPublicKey=...
app.push.vapidPrivateKey=...
app.push.subject=mailto:you@example.com
```
A quick way:
```
npx web-push generate-vapid-keys
```

### Storage (MinIO)
Default dev credentials are already set in `application.yml`:
```
app.storage.endpoint=http://localhost:9000
app.storage.accessKey=admin
app.storage.secretKey=adminadmin
app.storage.bucket=cove
```
MinIO console: http://localhost:9001

## Migrating from SwiftGram
The rename changed the dev database name/credentials (`swiftgram`/`swift` → `cove`/`cove`) and the MinIO bucket (`swiftgram` → `cove`). Existing Docker volumes were initialized with the old names, so the easiest path is a clean reset (dev data only):

```
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d
```

In the browser, existing sessions and E2EE identity keys migrate automatically (`swiftgram_*` → `cove_*` localStorage keys). The JWT issuer changed, so users sign in again with a fresh OTP — their identity keys are preserved.

## Notes on E2EE
- Message bodies and attachments are encrypted client-side (NaCl secretbox + public-key sealed chat keys).
- Server stores **ciphertext only**; no server-side search.
- Metadata (sender, timestamps, sizes) is still visible to the server (typical for E2EE systems).

## Health check
```
http://localhost:8080/health
```

## Dev services
- Postgres: `localhost:5432` (user: `cove`, password: `covepass`, db: `cove`)
- Redis: `localhost:6379`
- NATS: `localhost:4222`
- MinIO: `localhost:9000` / console `:9001`
- Mailpit: `localhost:8025`
