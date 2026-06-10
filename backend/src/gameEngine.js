// UNO Game Engine - Core Logic

const COLORS = ['red', 'green', 'blue', 'yellow'];
const VALUES = ['0','1','2','3','4','5','6','7','8','9','skip','reverse','draw2'];
const WILD_CARDS = ['wild', 'wild_draw4'];

class UnoCard {
    constructor(color, value) {
        this.color = color; // red, green, blue, yellow, wild
        this.value = value; // 0-9, skip, reverse, draw2, wild, wild_draw4
        this.id = `${color}_${value}_${Math.random().toString(36).substr(2,5)}`;
    }
}

class UnoGame {
    constructor(roomId, players) {
        this.roomId = roomId;
        this.players = players.map(p => ({ ...p, hand: [], saidUno: false }));
        this.deck = [];
        this.discardPile = [];
        this.currentPlayerIndex = 0;
        this.direction = 1; // 1 = clockwise, -1 = counter-clockwise
        this.status = 'waiting'; // waiting, playing, finished
        this.currentColor = null;
        this.drawStack = 0; // for stacking draw2 / draw4
        this.winner = null;
        this.pendingDraw = 0;
        this.turnSkipped = false;
    }

    buildDeck() {
        const deck = [];
        for (const color of COLORS) {
            // One 0, two of each 1-9 and action cards
            deck.push(new UnoCard(color, '0'));
            for (const value of VALUES.slice(1)) {
                deck.push(new UnoCard(color, value));
                deck.push(new UnoCard(color, value));
            }
        }
        // Wild cards x4 each
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

        // Deal 7 cards to each player
        for (let i = 0; i < 7; i++) {
            for (const player of this.players) {
                player.hand.push(this.drawFromDeck());
            }
        }

        // Flip first card - must not be a wild
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

        // Handle if first card is action card
        this.applyFirstCardEffect(firstCard);

        return this.getState();
    }

    applyFirstCardEffect(card) {
        if (card.value === 'skip') {
            this.currentPlayerIndex = this.getNextPlayerIndex();
        } else if (card.value === 'reverse') {
            this.direction = -1;
        } else if (card.value === 'draw2') {
            this.pendingDraw = 2;
        }
    }

    drawFromDeck() {
        if (this.deck.length === 0) {
            // Reshuffle discard pile except top card
            const topCard = this.discardPile.pop();
            this.deck = this.shuffle(this.discardPile);
            this.discardPile = [topCard];
        }
        return this.deck.pop();
    }

    getNextPlayerIndex(from = this.currentPlayerIndex) {
        return (from + this.direction + this.players.length) % this.players.length;
    }

    getCurrentPlayer() {
        return this.players[this.currentPlayerIndex];
    }

    getTopCard() {
        return this.discardPile[this.discardPile.length - 1];
    }

    isValidPlay(card) {
        const top = this.getTopCard();
        if (card.color === 'wild') return true;
        if (this.pendingDraw > 0) {
            // Can only stack draw cards
            if (top.value === 'draw2') return card.value === 'draw2';
            if (top.value === 'wild_draw4') return card.value === 'wild_draw4';
            return false;
        }
        return card.color === this.currentColor || card.value === top.value;
    }

    playCard(playerId, cardId, chosenColor = null) {
        const player = this.players.find(p => p.id === playerId);
        if (!player) return { success: false, error: 'Player not found' };
        if (this.players[this.currentPlayerIndex].id !== playerId)
            return { success: false, error: 'Not your turn' };

        const cardIndex = player.hand.findIndex(c => c.id === cardId);
        if (cardIndex === -1) return { success: false, error: 'Card not in hand' };

        const card = player.hand[cardIndex];
        if (!this.isValidPlay(card)) return { success: false, error: 'Invalid play' };

        // Remove from hand
        player.hand.splice(cardIndex, 1);
        player.saidUno = false;
        this.discardPile.push(card);

        // Apply card effect
        const effects = this.applyCardEffect(card, chosenColor);

        // Check win condition
        if (player.hand.length === 0) {
            this.status = 'finished';
            this.winner = playerId;
            return { success: true, card, effects, winner: playerId, state: this.getState() };
        }

        // Check UNO (1 card left)
        const unoAlert = player.hand.length === 1;

        return { success: true, card, effects, unoAlert, state: this.getState() };
    }

    applyCardEffect(card, chosenColor) {
        const effects = { type: card.value };

        if (card.color === 'wild') {
            this.currentColor = chosenColor || 'red';
        } else {
            this.currentColor = card.color;
        }

        switch (card.value) {
            case 'skip':
                this.currentPlayerIndex = this.getNextPlayerIndex();
                this.currentPlayerIndex = this.getNextPlayerIndex();
                effects.skipped = this.players[this.getNextPlayerIndex(
                    (this.currentPlayerIndex - this.direction + this.players.length) % this.players.length
                )].id;
                break;
            case 'reverse':
                this.direction *= -1;
                if (this.players.length === 2) {
                    // In 2-player, reverse acts like skip
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
        if (this.players[this.currentPlayerIndex].id !== playerId)
            return { success: false, error: 'Not your turn' };

        let drawnCards = [];
        const drawCount = this.pendingDraw > 0 ? this.pendingDraw : 1;
        this.pendingDraw = 0;

        for (let i = 0; i < drawCount; i++) {
            const card = this.drawFromDeck();
            player.hand.push(card);
            drawnCards.push(card);
        }

        // After drawing penalty cards, skip to next player
        if (drawCount > 1) {
            this.currentPlayerIndex = this.getNextPlayerIndex();
        } else {
            // Check if drawn card is playable
            const playable = drawnCards[0] && this.isValidPlay(drawnCards[0]);
            if (!playable) {
                this.currentPlayerIndex = this.getNextPlayerIndex();
            }
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
        // Penalty: target draws 2 cards
        for (let i = 0; i < 2; i++) target.hand.push(this.drawFromDeck());
        return { success: true, penalized: targetId, state: this.getState() };
    }

    getState(forPlayerId = null) {
        return {
            roomId: this.roomId,
            status: this.status,
            currentPlayerId: this.getCurrentPlayer()?.id,
            currentColor: this.currentColor,
            direction: this.direction,
            pendingDraw: this.pendingDraw,
            topCard: this.getTopCard(),
            deckCount: this.deck.length,
            winner: this.winner,
            players: this.players.map(p => ({
                id: p.id,
                username: p.username,
                cardCount: p.hand.length,
                saidUno: p.saidUno,
                // Only reveal hand to the requesting player
                hand: forPlayerId === p.id ? p.hand : null
            }))
        };
    }

    getPlayerState(playerId) {
        return this.getState(playerId);
    }
}

module.exports = { UnoGame };
