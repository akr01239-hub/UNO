-- UNO Game Database Schema for Neon PostgreSQL

CREATE TABLE IF NOT EXISTS players (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    avatar_color VARCHAR(10) DEFAULT '#FF6B6B',
    games_played INTEGER DEFAULT 0,
    games_won INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_code VARCHAR(8) UNIQUE NOT NULL,
    host_id UUID REFERENCES players(id),
    status VARCHAR(20) DEFAULT 'waiting', -- waiting, playing, finished
    max_players INTEGER DEFAULT 6,
    current_players INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS room_players (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID REFERENCES rooms(id) ON DELETE CASCADE,
    player_id UUID REFERENCES players(id),
    seat_position INTEGER NOT NULL,
    is_connected BOOLEAN DEFAULT true,
    joined_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(room_id, player_id),
    UNIQUE(room_id, seat_position)
);

CREATE TABLE IF NOT EXISTS game_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID REFERENCES rooms(id),
    winner_id UUID REFERENCES players(id),
    total_turns INTEGER DEFAULT 0,
    started_at TIMESTAMP DEFAULT NOW(),
    ended_at TIMESTAMP,
    game_state JSONB -- stores full game state snapshot
);

CREATE TABLE IF NOT EXISTS game_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES game_sessions(id),
    player_id UUID REFERENCES players(id),
    event_type VARCHAR(50) NOT NULL, -- play_card, draw_card, say_uno, skip, etc.
    event_data JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_rooms_code ON rooms(room_code);
CREATE INDEX IF NOT EXISTS idx_room_players_room ON room_players(room_id);
CREATE INDEX IF NOT EXISTS idx_game_events_session ON game_events(session_id);

-- Function to update room timestamp
CREATE OR REPLACE FUNCTION update_room_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER room_updated
BEFORE UPDATE ON rooms
FOR EACH ROW EXECUTE FUNCTION update_room_timestamp();
