require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const { Pool } = require('pg');
const { v4: uuidv4 } = require('uuid');
const { UnoGame } = require('./gameEngine');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: { origin: '*', methods: ['GET', 'POST'] }
});

app.use(cors());
app.use(express.json());

// Neon DB Connection
const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false }
});

// In-memory game state (fast access)
const activeGames = {}; // roomCode -> UnoGame instance
const socketToPlayer = {}; // socketId -> { playerId, roomCode }
const roomVoiceStreams = {}; // roomCode -> Set of socketIds

// ─── REST ENDPOINTS ──────────────────────────────────────────────────────────

app.get('/health', (req, res) => res.json({ status: 'ok', timestamp: new Date() }));

// Register / login player
app.post('/api/players', async (req, res) => {
    const { username, avatarColor } = req.body;
    if (!username) return res.status(400).json({ error: 'Username required' });
    try {
        const result = await pool.query(
            `INSERT INTO players (username, avatar_color)
             VALUES ($1, $2)
             ON CONFLICT (username) DO UPDATE SET avatar_color = $2
             RETURNING *`,
            [username.trim(), avatarColor || '#FF6B6B']
        );
        res.json(result.rows[0]);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Get player stats
app.get('/api/players/:id/stats', async (req, res) => {
    try {
        const result = await pool.query(
            'SELECT username, games_played, games_won, avatar_color FROM players WHERE id = $1',
            [req.params.id]
        );
        if (!result.rows.length) return res.status(404).json({ error: 'Player not found' });
        res.json(result.rows[0]);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Create room
app.post('/api/rooms', async (req, res) => {
    const { hostId, maxPlayers } = req.body;
    const code = generateRoomCode();
    try {
        const result = await pool.query(
            `INSERT INTO rooms (room_code, host_id, max_players)
             VALUES ($1, $2, $3) RETURNING *`,
            [code, hostId, Math.min(maxPlayers || 6, 6)]
        );
        res.json(result.rows[0]);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Get room info
app.get('/api/rooms/:code', async (req, res) => {
    try {
        const room = await pool.query(
            'SELECT * FROM rooms WHERE room_code = $1', [req.params.code]
        );
        if (!room.rows.length) return res.status(404).json({ error: 'Room not found' });
        const players = await pool.query(
            `SELECT rp.seat_position, rp.is_connected, p.id, p.username, p.avatar_color
             FROM room_players rp JOIN players p ON p.id = rp.player_id
             WHERE rp.room_id = $1 ORDER BY rp.seat_position`,
            [room.rows[0].id]
        );
        res.json({ ...room.rows[0], players: players.rows });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─── SOCKET.IO ───────────────────────────────────────────────────────────────

io.on('connection', (socket) => {
    console.log(`[Socket] Connected: ${socket.id}`);

    // Join room (also handles rejoining mid-game from GameActivity)
    socket.on('join_room', async ({ roomCode, playerId, username }) => {
        try {
            const roomResult = await pool.query(
                'SELECT * FROM rooms WHERE room_code = $1', [roomCode]
            );
            if (!roomResult.rows.length) {
                socket.emit('error', { message: 'Room not found' });
                return;
            }
            const room = roomResult.rows[0];

            // If game is already playing, this is a socket rejoin (e.g. GameActivity starting)
            // Just re-join the socket room and send current state — don't touch DB counts
            if (room.status === 'playing') {
                socket.join(roomCode);
                socketToPlayer[socket.id] = { playerId, roomCode, username };
                console.log(`[Room] ${username} rejoined playing room ${roomCode}`);

                const game = activeGames[roomCode];
                if (game) {
                    socket.emit('game_state', game.getPlayerState(playerId));
                }
                return;
            }

            if (room.current_players >= room.max_players) {
                socket.emit('error', { message: 'Room is full' });
                return;
            }

            // Add player to room in DB
            const seatResult = await pool.query(
                `SELECT COALESCE(MAX(seat_position), -1) + 1 AS next_seat
                 FROM room_players WHERE room_id = $1`, [room.id]
            );
            const seatPos = seatResult.rows[0].next_seat;

            await pool.query(
                `INSERT INTO room_players (room_id, player_id, seat_position)
                 VALUES ($1, $2, $3)
                 ON CONFLICT (room_id, player_id) DO UPDATE SET is_connected = true`,
                [room.id, playerId, seatPos]
            );
            await pool.query(
                'UPDATE rooms SET current_players = current_players + 1 WHERE id = $1',
                [room.id]
            );

            socket.join(roomCode);
            socketToPlayer[socket.id] = { playerId, roomCode, username };
            if (!roomVoiceStreams[roomCode]) roomVoiceStreams[roomCode] = new Set();

            const players = await getRoomPlayers(room.id);
            io.to(roomCode).emit('room_update', {
                roomCode,
                players,
                hostId: room.host_id,
                status: room.status
            });

            console.log(`[Room] ${username} joined ${roomCode} (seat ${seatPos})`);
        } catch (err) {
            socket.emit('error', { message: err.message });
        }
    });

    // Start game (host only)
    socket.on('start_game', async ({ roomCode, playerId }) => {
        try {
            const roomResult = await pool.query(
                'SELECT * FROM rooms WHERE room_code = $1', [roomCode]
            );
            const room = roomResult.rows[0];
            if (!room || room.host_id !== playerId) {
                socket.emit('error', { message: 'Only host can start' });
                return;
            }
            if (room.current_players < 2) {
                socket.emit('error', { message: 'Need at least 2 players' });
                return;
            }

            const playerRows = await getRoomPlayers(room.id);
            const game = new UnoGame(roomCode, playerRows);
            activeGames[roomCode] = game;
            const state = game.start();

            await pool.query(
                "UPDATE rooms SET status = 'playing' WHERE id = $1", [room.id]
            );

            // Create game session record
            await pool.query(
                'INSERT INTO game_sessions (room_id, game_state) VALUES ($1, $2)',
                [room.id, JSON.stringify(state)]
            );

            // Send each player their own hand
            const sockets = await io.in(roomCode).fetchSockets();
            for (const s of sockets) {
                const sp = socketToPlayer[s.id];
                if (sp) {
                    s.emit('game_started', game.getPlayerState(sp.playerId));
                }
            }

            console.log(`[Game] Started in room ${roomCode} with ${playerRows.length} players`);
        } catch (err) {
            socket.emit('error', { message: err.message });
        }
    });

    // Play card
    socket.on('play_card', async ({ roomCode, playerId, cardId, chosenColor }) => {
        const game = activeGames[roomCode];
        if (!game) { socket.emit('error', { message: 'No active game' }); return; }

        const result = game.playCard(playerId, cardId, chosenColor);
        if (!result.success) { socket.emit('error', { message: result.error }); return; }

        await logEvent(roomCode, playerId, 'play_card', { cardId, chosenColor });

        if (result.winner) {
            // Update stats
            await pool.query(
                'UPDATE players SET games_won = games_won + 1 WHERE id = $1', [result.winner]
            );
            await pool.query(
                `UPDATE players SET games_played = games_played + 1
                 WHERE id = ANY($1::uuid[])`,
                [game.players.map(p => p.id)]
            );
            await pool.query(
                "UPDATE rooms SET status = 'finished' WHERE room_code = $1", [roomCode]
            );
        }

        // Broadcast to all, send hands individually
        await broadcastGameState(roomCode, game, result);
    });

    // Draw card
    socket.on('draw_card', async ({ roomCode, playerId }) => {
        const game = activeGames[roomCode];
        if (!game) { socket.emit('error', { message: 'No active game' }); return; }

        const result = game.drawCard(playerId);
        if (!result.success) { socket.emit('error', { message: result.error }); return; }

        await logEvent(roomCode, playerId, 'draw_card', { count: result.drawCount });
        await broadcastGameState(roomCode, game, result);
    });

    // Say UNO
    socket.on('say_uno', ({ roomCode, playerId }) => {
        const game = activeGames[roomCode];
        if (!game) return;
        const result = game.sayUno(playerId);
        if (result.success) {
            io.to(roomCode).emit('uno_called', { playerId });
        }
    });

    // Challenge UNO
    socket.on('challenge_uno', async ({ roomCode, challengerId, targetId }) => {
        const game = activeGames[roomCode];
        if (!game) return;
        const result = game.challengeUno(challengerId, targetId);
        io.to(roomCode).emit('uno_challenge', { challengerId, targetId, success: result.success });
        if (result.success) {
            await broadcastGameState(roomCode, game, result);
        }
    });

    // Get current game state (called by GameActivity on connect)
    socket.on('get_game_state', ({ roomCode }) => {
        const sp = socketToPlayer[socket.id];
        const game = activeGames[roomCode];
        if (game && sp) {
            socket.emit('game_state', game.getPlayerState(sp.playerId));
        }
    });

    // ── Voice Chat (WebRTC Signaling) ──────────────────────────────────────

    socket.on('voice_join', ({ roomCode }) => {
        if (roomVoiceStreams[roomCode]) {
            roomVoiceStreams[roomCode].add(socket.id);
        }
        socket.to(roomCode).emit('voice_peer_joined', { socketId: socket.id });
    });

    socket.on('voice_offer', ({ targetSocketId, offer, roomCode }) => {
        io.to(targetSocketId).emit('voice_offer', {
            fromSocketId: socket.id,
            offer
        });
    });

    socket.on('voice_answer', ({ targetSocketId, answer }) => {
        io.to(targetSocketId).emit('voice_answer', {
            fromSocketId: socket.id,
            answer
        });
    });

    socket.on('voice_ice_candidate', ({ targetSocketId, candidate }) => {
        io.to(targetSocketId).emit('voice_ice_candidate', {
            fromSocketId: socket.id,
            candidate
        });
    });

    socket.on('voice_leave', ({ roomCode }) => {
        if (roomVoiceStreams[roomCode]) {
            roomVoiceStreams[roomCode].delete(socket.id);
        }
        socket.to(roomCode).emit('voice_peer_left', { socketId: socket.id });
    });

    // ── Disconnect ─────────────────────────────────────────────────────────

    socket.on('disconnect', async () => {
        const sp = socketToPlayer[socket.id];
        if (sp) {
            const { playerId, roomCode } = sp;
            // Mark disconnected in DB
            await pool.query(
                `UPDATE room_players rp SET is_connected = false
                 FROM rooms r WHERE r.id = rp.room_id
                 AND r.room_code = $1 AND rp.player_id = $2`,
                [roomCode, playerId]
            ).catch(() => {});

            if (roomVoiceStreams[roomCode]) {
                roomVoiceStreams[roomCode].delete(socket.id);
            }
            socket.to(roomCode).emit('player_disconnected', { playerId });
            delete socketToPlayer[socket.id];
            console.log(`[Socket] Disconnected: ${socket.id} (${sp.username})`);
        }
    });
});

// ─── HELPERS ─────────────────────────────────────────────────────────────────

function generateRoomCode() {
    return Math.random().toString(36).substr(2, 6).toUpperCase();
}

async function getRoomPlayers(roomId) {
    const result = await pool.query(
        `SELECT p.id, p.username, p.avatar_color, rp.seat_position
         FROM room_players rp JOIN players p ON p.id = rp.player_id
         WHERE rp.room_id = $1 AND rp.is_connected = true
         ORDER BY rp.seat_position`,
        [roomId]
    );
    return result.rows;
}

async function broadcastGameState(roomCode, game, eventResult) {
    const sockets = await io.in(roomCode).fetchSockets();
    for (const s of sockets) {
        const sp = socketToPlayer[s.id];
        if (sp) {
            s.emit('game_state', {
                ...game.getPlayerState(sp.playerId),
                lastEvent: eventResult
            });
        }
    }
}

async function logEvent(roomCode, playerId, eventType, eventData) {
    try {
        const roomResult = await pool.query(
            `SELECT gs.id FROM game_sessions gs
             JOIN rooms r ON r.id = gs.room_id
             WHERE r.room_code = $1 ORDER BY gs.started_at DESC LIMIT 1`,
            [roomCode]
        );
        if (roomResult.rows.length) {
            await pool.query(
                'INSERT INTO game_events (session_id, player_id, event_type, event_data) VALUES ($1,$2,$3,$4)',
                [roomResult.rows[0].id, playerId, eventType, JSON.stringify(eventData)]
            );
        }
    } catch (_) {}
}

// ─── START ────────────────────────────────────────────────────────────────────

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`🃏 UNO Server running on port ${PORT}`);
    pool.query('SELECT NOW()').then(() => console.log('✅ Neon DB connected'))
        .catch(err => console.error('❌ DB error:', err.message));
});
