# 🃏 UNO Online — Multiplayer Card Game

A full-stack UNO card game for up to **6 players** with real-time gameplay, voice chat, and beautiful colorful cards.

---

## 📁 Project Structure

```
UNO/
├── backend/          # Node.js + Socket.IO server
│   ├── src/
│   │   ├── server.js       # Main server (Express + Socket.IO)
│   │   └── gameEngine.js   # UNO game logic engine
│   ├── schema.sql          # Neon PostgreSQL schema
│   ├── package.json
│   ├── Dockerfile
│   └── .env.example
│
└── android/          # Kotlin Android App
    ├── app/src/main/
    │   ├── java/com/uno/game/
    │   │   ├── models/         # Data models (Card, Player, Room, GameState)
    │   │   ├── network/        # SocketManager + ApiService
    │   │   ├── audio/          # VoiceChatManager (WebRTC) + SoundManager
    │   │   ├── ui/
    │   │   │   ├── home/       # HomeActivity (create/join room)
    │   │   │   ├── lobby/      # LobbyActivity (wait for players)
    │   │   │   └── game/       # GameActivity + adapters + custom card view
    │   │   └── utils/          # PreferencesManager
    │   └── res/                # Layouts, drawables, themes
    └── build.gradle
```

---

## 🚀 Backend Setup

### 1. Create Neon Database
1. Go to [neon.tech](https://neon.tech) → New Project
2. Copy your **DATABASE_URL** (looks like `postgresql://user:pass@ep-xxx.neon.tech/uno_db?sslmode=require`)
3. Run the schema: paste `schema.sql` into Neon's SQL editor and execute

### 2. Run Locally
```bash
cd backend
npm install
cp .env.example .env
# Edit .env and paste your DATABASE_URL
npm run dev
```

### 3. Deploy (Railway / Render / Fly.io)
```bash
# Railway (recommended)
railway login
railway init
railway add --database postgresql  # or use Neon URL
railway up
```

Set environment variable: `DATABASE_URL=your_neon_url`

---

## 📱 Android Setup

### 1. Configure Server URL
Open `android/app/build.gradle` and change:
```groovy
buildConfigField "String", "SERVER_URL", '"http://YOUR_SERVER_IP:3000"'
```
- For emulator testing: `"http://10.0.2.2:3000"`
- For real device on same WiFi: `"http://192.168.x.x:3000"`
- For production: `"https://your-deployed-server.com"`

### 2. Build APK in Android Studio
1. Open `android/` folder in Android Studio
2. Wait for Gradle sync
3. Build → Generate Signed Bundle/APK → APK
4. Install on device

### 3. Required Permissions (already in Manifest)
- `INTERNET` — online play
- `RECORD_AUDIO` — voice chat
- `MODIFY_AUDIO_SETTINGS` — echo cancellation

---

## 🎮 How to Play

1. **Launch the app** → enter username → pick avatar color
2. **Create Room** (you become host) or **Join Room** (enter 6-char code)
3. Share the room code with up to 5 friends
4. Host taps **START GAME** (min 2 players)
5. Each player gets **7 cards** — play begins!

### Game Rules Implemented
- ✅ 7 cards dealt to each player
- ✅ Skip, Reverse, Draw 2 action cards
- ✅ Wild & Wild +4 with color picker
- ✅ Card stacking (draw cards can be stacked)
- ✅ **Auto UNO alert** when 1 card remains 🔊
- ✅ UNO button — tap when you have 1 card
- ✅ Challenge UNO — long-press opponent with 1 card to challenge
- ✅ Direction indicator (clockwise / counter-clockwise)
- ✅ Win detection + stats saved to DB

---

## 🎙️ Voice Chat
- Tap the 🎤 mic button in-game to join voice
- Uses **WebRTC peer-to-peer** — low latency
- Tap again to mute/unmute
- Microphone permission is requested automatically
- Echo cancellation + noise suppression enabled

---

## 🔊 Sound Effects
Add these files to `android/app/src/main/res/raw/`:
```
sound_uno.mp3        — UNO alert
sound_card_play.mp3  — card played
sound_card_draw.mp3  — draw card
sound_win.mp3        — win fanfare
sound_reverse.mp3    — reverse card
sound_skip.mp3       — skip card
sound_draw4.mp3      — draw 4 card
```
Free sounds: [freesound.org](https://freesound.org) or [mixkit.co](https://mixkit.co/free-sound-effects/)

---

## 🗄️ Database (Neon PostgreSQL)

| Table | Purpose |
|-------|---------|
| `players` | Usernames, avatar colors, win stats |
| `rooms` | Room codes, host, player count, status |
| `room_players` | Which players are in which rooms |
| `game_sessions` | Game history + final state snapshots |
| `game_events` | Every card play/draw logged |

---

## 🌐 Socket.IO Events

| Event | Direction | Description |
|-------|-----------|-------------|
| `join_room` | Client → Server | Join a room |
| `start_game` | Client → Server | Host starts the game |
| `play_card` | Client → Server | Play a card |
| `draw_card` | Client → Server | Draw from deck |
| `say_uno` | Client → Server | Declare UNO |
| `challenge_uno` | Client → Server | Challenge another player |
| `room_update` | Server → Client | Room players list updated |
| `game_started` | Server → Client | Game begins with initial state |
| `game_state` | Server → Client | Updated game state after each action |
| `uno_called` | Server → Client | A player declared UNO |
| `voice_*` | Both | WebRTC signaling for voice chat |

---

## 🛠️ Tech Stack

| Layer | Tech |
|-------|------|
| Backend | Node.js + Express + Socket.IO |
| Database | Neon (serverless PostgreSQL) |
| Android | Kotlin + ViewBinding + RecyclerView |
| Realtime | Socket.IO (WebSocket) |
| Voice | WebRTC (stream-webrtc-android) |
| Cards | Custom `UnoCardView` drawn with Canvas API |
| Networking | OkHttp (REST) + Socket.IO client |
