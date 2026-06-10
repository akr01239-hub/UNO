package com.uno.game.audio

import android.content.Context
import android.util.Log
import com.uno.game.network.SocketManager
import org.json.JSONObject
import org.webrtc.*

class VoiceChatManager(private val context: Context, private val roomCode: String) {
    private val TAG = "VoiceChat"
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private val peerConnections = HashMap<String, PeerConnection>()
    private var localAudioTrack: AudioTrack? = null
    private var isMuted = false

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        val options = PeerConnectionFactory.Options()
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_local", audioSource)

        // Setup socket callbacks for signaling
        SocketManager.onVoicePeerJoined = { socketId ->
            Log.d(TAG, "Voice peer joined: $socketId")
            createOfferFor(socketId)
        }
        SocketManager.onVoicePeerLeft = { socketId ->
            peerConnections[socketId]?.close()
            peerConnections.remove(socketId)
        }
        SocketManager.onVoiceOffer = { fromSocketId, offer ->
            handleOffer(fromSocketId, offer)
        }
        SocketManager.onVoiceAnswer = { fromSocketId, answer ->
            handleAnswer(fromSocketId, answer)
        }
        SocketManager.onVoiceIceCandidate = { fromSocketId, candidate ->
            handleIceCandidate(fromSocketId, candidate)
        }

        SocketManager.joinVoice(roomCode)
        Log.d(TAG, "Voice chat initialized for room $roomCode")
    }

    private fun createPeerConnection(remoteSocketId: String): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        val conn = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val json = JSONObject().apply {
                        put("sdpMid", it.sdpMid)
                        put("sdpMLineIndex", it.sdpMLineIndex)
                        put("candidate", it.sdp)
                    }
                    SocketManager.sendIceCandidate(remoteSocketId, json)
                }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "Connection state to $remoteSocketId: $state")
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onTrack(transceiver: RtpTransceiver?) {}
        })
        conn?.addTrack(localAudioTrack)
        peerConnections[remoteSocketId] = conn!!
        return conn
    }

    private fun createOfferFor(remoteSocketId: String) {
        val conn = createPeerConnection(remoteSocketId) ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        conn.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                conn.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        val json = JSONObject().apply {
                            put("type", sdp?.type?.canonicalForm())
                            put("sdp", sdp?.description)
                        }
                        SocketManager.sendVoiceOffer(remoteSocketId, json, roomCode)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun handleOffer(fromSocketId: String, offer: Any) {
        val json = JSONObject(offer.toString())
        val sdp = SessionDescription(
            SessionDescription.Type.fromCanonicalForm(json.optString("type")),
            json.optString("sdp")
        )
        val conn = createPeerConnection(fromSocketId) ?: return
        conn.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                conn.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerSdp: SessionDescription?) {
                        conn.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                val answerJson = JSONObject().apply {
                                    put("type", answerSdp?.type?.canonicalForm())
                                    put("sdp", answerSdp?.description)
                                }
                                SocketManager.sendVoiceAnswer(fromSocketId, answerJson)
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, answerSdp)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, constraints)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    private fun handleAnswer(fromSocketId: String, answer: Any) {
        val json = JSONObject(answer.toString())
        val sdp = SessionDescription(
            SessionDescription.Type.fromCanonicalForm(json.optString("type")),
            json.optString("sdp")
        )
        peerConnections[fromSocketId]?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() { Log.d(TAG, "Remote description set for $fromSocketId") }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    private fun handleIceCandidate(fromSocketId: String, candidate: Any) {
        val json = JSONObject(candidate.toString())
        val iceCandidate = IceCandidate(
            json.optString("sdpMid"),
            json.optInt("sdpMLineIndex"),
            json.optString("candidate")
        )
        peerConnections[fromSocketId]?.addIceCandidate(iceCandidate)
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        localAudioTrack?.setEnabled(!isMuted)
        return isMuted
    }

    fun isMuted() = isMuted

    fun release() {
        SocketManager.leaveVoice(roomCode)
        peerConnections.values.forEach { it.close() }
        peerConnections.clear()
        localAudioTrack?.dispose()
        peerConnectionFactory.dispose()
    }
}
