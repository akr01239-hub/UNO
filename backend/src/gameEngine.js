// UNO Game Engine - Core Logic

const COLORS = ['red', 'green', 'blue', 'yellow'];
const VALUES = ['0','1','2','3','4','5','6','7','8','9','skip','reverse','draw2'];

class UnoCard {
    constructor(color, value) {
        this.color = color;
        this.value = value;
        this.id = `${color}_${value}_${Math.random().toString(36).substr(2,5)}`;
    }
}

class UnoGame {
    constructor(roomId, players) {
        this.roomId = roomId;
        this.players = players.map(p => ({ ...p, hand: [], saidUno: false, finishPosition: null }));
        this.deck = [];
        this.discardPile = [];
        this.currentPlayerIndex = 0;   // index into activePlayers array
        this.direction = 1;
        this.status = 'waiting';
        this.currentColor = null;
        this.pendingDraw = 0;
        this.winner = null;            // first-place player id
        this.finishOrder = [];         // [1st, 2nd, 3rd ...] player ids
    }

    // ── Active players only (not yet finished) ────────────────────────────────
    get activePlayers() {
        return this.players.filter(p => p.finishPosition === null);
    }

    getCurrentPlayer() {
        const active = this.activePlayers;
        if (active.length === 0) return null;
        return active[this.currentPlayerIndex % active.length];
    }

    // Returns the index (in activePlayers) of the next player
    getNextPlayerIndex(from = this.currentPlayerIndex) {
        const active = this.activePlayers.length;
        if (active === 0) return 0;
        return ((from + this.direction) % active + active) % active;
    }

    buildDeck() {
        const deck = [];
        for (const color of COLORS) {
            deck.push(new UnoCard(color, '0'));
            for (const value of VALUES.slice(1)) {
                deck.push(new UnoCard(color, value));
                deck.push(new UnoCard(color, value));
            }
        }
        for (let i = 0; i < 4; i++) {
            deck.push(new UnoCard('wild', 'wild'));
            deck.push(new UnoCard('wild', 'wild_draw4'));
        }
        return this.shuffle(deck);
    }

    shuffle(array) {
        const arr = [...array];
        for (let i = arr.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [arr[i], arr[j]] = [arr[j], arr[i]];
        }
        return arr;
    }

    start() {
        this.deck = this.buildDeck();
        this.status = 'playing';
        for (let i = 0; i < 7; i++) {
            for (const player of this.players) {
                player.hand.push(this.drawFromDeck());
            }
        }
        let firstCard;
        do {
            firstCard = this.drawFromDeck();
            if (firstCard.color === 'wild') {
                this.deck.push(firstCard);
                this.deck = this.shuffle(this.deck);
            }
        } while (firstCard.color === 'wild');
        this.discardPile.push(firstCard);
        this.currentColor = firstCard.color;
        this.applyFirstCardEffect(firstCard);
        return this.getState();
    }

    applyFirstCardEffect(card) {
        if (card.value === 'skip') this.currentPlayerIndex = this.getNextPlayerIndex();
        else if (card.value === 'reverse') this.direction = -1;
        else if (card.value === 'draw2') this.pendingDraw = 2;
    }

    drawFromDeck() {
        if (this.deck.length === 0) {
            const topCard = this.discardPile.pop();
            this.deck = this.shuffle(this.discardPile);
            this.discardPile = [topCard];
        }
        return this.deck.pop();
    }

    getTopCard() {
        return this.discardPile[this.discardPile.length - 1];
    }

    isValidPlay(card) {
        const top = this.getTopCard();
        if (card.color === 'wild') return true;
        if (this.pendingDraw > 0) {
            if (top.value === 'draw2')      return card.value === 'draw2';
            if (top.value === 'wild_draw4') return card.value === 'wild_draw4';
            return false;
        }
        return card.color === this.currentColor || card.value === top.value;
    }

    playCard(playerId, cardId, chosenColor = null) {
        const player = this.players.find(p => p.id === playerId);
        if (!player) return { success: false, error: 'Player not found' };
        if (this.getCurrentPlayer()?.id !== playerId)
            return { success: false, error: 'Not your turn' };

        const cardIndex = player.hand.findIndex(c => c.id === cardId);
        if (cardIndex === -1) return { success: false, error: 'Card not in hand' };

        const card = player.hand[cardIndex];
        if (!this.isValidPlay(card)) return { success: false, error: 'Invalid play' };

        player.hand.splice(cardIndex, 1);
        player.saidUno = false;
        this.discardPile.push(card);

        // ── Player finished! ───────────────────────────────────────────────────
        if (player.hand.length === 0) {
            const position = this.finishOrder.length + 1;
            player.finishPosition = position;
            this.finishOrder.push(playerId);
            if (!this.winner) this.winner = playerId;

            // Apply color change for wild cards even when finishing
            if (card.color === 'wild') {
                this.currentColor = chosenColor || 'red';
            }

            // FIX: After removing this player from activePlayers, clamp the index
            // so it points to the correct NEXT player (not the same slot which is
            // now a different person, or out of range).
            const remaining = this.activePlayers;   // player already has finishPosition set

            // Game fully over — only 1 active player left (the loser)
            if (remaining.length <= 1) {
                if (remaining.length === 1) {
                    const lastPlayer = remaining[0];
                    lastPlayer.finishPosition = this.finishOrder.length + 1;
                    this.finishOrder.push(lastPlayer.id);
                }
                this.status = 'finished';
                return {
                    success: true, card,
                    playerFinished: playerId, finishPosition: position,
                    winner: this.winner,
                    finalRankings: this.finishOrder,
                    state: this.getState()
                };
            }

            // FIX: Advance to the next player correctly.
            // currentPlayerIndex was pointing at the finishing player's slot.
            // After removal the array shrank, so we must NOT just mod — instead
            // we advance by the direction so the correct next person gets the turn.
            // We keep the same index value which now naturally lands on the person
            // who was AFTER the finishing player (array shifted left).
            this.currentPlayerIndex = this.currentPlayerIndex % remaining.length;

            return {
                success: true, card,
                playerFinished: playerId, finishPosition: position,
                unoAlert: false,
                state: this.getState()
            };
        }

        // Normal play — apply card effects and advance turn
        const effects = this.applyCardEffect(card, chosenColor);
        const unoAlert = player.hand.length === 1;
        return { success: true, card, effects, unoAlert, state: this.getState() };
    }

    applyCardEffect(card, chosenColor) {
        const effects = { type: card.value };
        this.currentColor = card.color === 'wild' ? (chosenColor || 'red') : card.color;
        const active = this.activePlayers.length;

        switch (card.value) {
            case 'skip':
                // Skip next player: advance twice
                this.currentPlayerIndex = this.getNextPlayerIndex();
                this.currentPlayerIndex = this.getNextPlayerIndex();
                break;
            case 'reverse':
                this.direction *= -1;
                if (active === 2) {
                    // 2-player reverse acts like skip
                    this.currentPlayerIndex = this.getNextPlayerIndex();
                    this.currentPlayerIndex = this.getNextPlayerIndex();
                } else {
                    this.currentPlayerIndex = this.getNextPlayerIndex();
                }
                break;
            case 'draw2':
                this.pendingDraw += 2;
                this.currentPlayerIndex = this.getNextPlayerIndex();
                break;
            case 'wild_draw4':
                this.pendingDraw += 4;
                this.currentPlayerIndex = this.getNextPlayerIndex();
                break;
            default:
                this.currentPlayerIndex = this.getNextPlayerIndex();
        }
        return effects;
    }

    drawCard(playerId) {
        const player = this.players.find(p => p.id === playerId);
        if (!player) return { success: false, error: 'Player not found' };
        if (this.getCurrentPlayer()?.id !== playerId)
            return { success: false, error: 'Not your turn' };

        const drawCount = this.pendingDraw > 0 ? this.pendingDraw : 1;
        this.pendingDraw = 0;
        const drawnCards = [];

        for (let i = 0; i < drawCount; i++) {
            const card = this.drawFromDeck();
            player.hand.push(card);
            drawnCards.push(card);
        }

        if (drawCount > 1) {
            // Penalty draw — forced, always pass turn
            this.currentPlayerIndex = this.getNextPlayerIndex();
        } else {
            // Normal draw — pass turn only if drawn card isn't playable
            const playable = drawnCards[0] && this.isValidPlay(drawnCards[0]);
            if (!playable) this.currentPlayerIndex = this.getNextPlayerIndex();
        }

        return { success: true, drawnCards, drawCount, state: this.getState() };
    }

    sayUno(playerId) {
        const player = this.players.find(p => p.id === playerId);
        if (!player) return { success: false, error: 'Player not found' };
        if (player.hand.length !== 1) return { success: false, error: 'Not on 1 card' };
        player.saidUno = true;
        return { success: true };
    }

    challengeUno(challengerId, targetId) {
        const target = this.players.find(p => p.id === targetId);
        if (!target) return { success: false, error: 'Target not found' };
        if (target.hand.length !== 1 || target.saidUno)
            return { success: false, error: 'Challenge failed' };
        for (let i = 0; i < 2; i++) target.hand.push(this.drawFromDeck());
        return { success: true, penalized: targetId, state: this.getState() };
    }

    getState() {
        return {
            roomId:          this.roomId,
            status:          this.status,
            currentPlayerId: this.getCurrentPlayer()?.id,
            currentColor:    this.currentColor,
            direction:       this.direction,
            pendingDraw:     this.pendingDraw,
            topCard:         this.getTopCard(),
            deckCount:       this.deck.length,
            winner:          this.winner,
            finishOrder:     this.finishOrder,
            players: this.players.map(p => ({
                id:             p.id,
                username:       p.username,
                cardCount:      p.hand.length,
                saidUno:        p.saidUno,
                finishPosition: p.finishPosition,
                hand:           null   // filled in getPlayerState
            }))
        };
    }

    getPlayerState(playerId) {
        const state = this.getState();
        state.players = this.players.map(p => ({
            id:             p.id,
            username:       p.username,
            cardCount:      p.hand.length,
            saidUno:        p.saidUno,
            finishPosition: p.finishPosition,
            hand:           p.id === playerId ? p.hand : null
        }));
        return state;
    }
}

module.exports = { UnoGame };
