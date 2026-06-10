package com.uno.game.ui.game

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uno.game.models.GameState
import com.uno.game.models.Player
import com.uno.game.network.SocketManager
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    private val _gameState = MutableLiveData<GameState>()
    val gameState: LiveData<GameState> = _gameState

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _unoEvent = MutableLiveData<String>() // playerId who said UNO
    val unoEvent: LiveData<String> = _unoEvent

    private val _winnerEvent = MutableLiveData<String>() // winner playerId
    val winnerEvent: LiveData<String> = _winnerEvent

    var currentPlayerId: String = ""
    var roomCode: String = ""

    fun initSocket() {
        SocketManager.onGameStarted = { state ->
            _gameState.postValue(state)
        }
        SocketManager.onGameState = { state ->
            _gameState.postValue(state)
            state.winner?.let { _winnerEvent.postValue(it) }
        }
        SocketManager.onUnoCalled = { playerId ->
            _unoEvent.postValue(playerId)
        }
        SocketManager.onError = { msg ->
            _errorMessage.postValue(msg)
        }
    }

    fun playCard(cardId: String, chosenColor: String? = null) {
        SocketManager.playCard(roomCode, currentPlayerId, cardId, chosenColor)
    }

    fun drawCard() {
        SocketManager.drawCard(roomCode, currentPlayerId)
    }

    fun sayUno() {
        SocketManager.sayUno(roomCode, currentPlayerId)
    }

    fun challengeUno(targetId: String) {
        SocketManager.challengeUno(roomCode, currentPlayerId, targetId)
    }

    fun isMyTurn(): Boolean {
        return _gameState.value?.currentPlayerId == currentPlayerId
    }

    fun getMyHand() = _gameState.value?.players
        ?.find { it.id == currentPlayerId }?.hand ?: emptyList()

    fun getOtherPlayers(): List<Player> =
        _gameState.value?.players?.filter { it.id != currentPlayerId } ?: emptyList()

    override fun onCleared() {
        super.onCleared()
        SocketManager.onGameStarted = null
        SocketManager.onGameState = null
        SocketManager.onUnoCalled = null
    }
}
