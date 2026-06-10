package com.uno.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.uno.game.R

object SoundManager {
    private lateinit var soundPool: SoundPool
    private var soundUno = 0
    private var soundCardPlay = 0
    private var soundCardDraw = 0
    private var soundWin = 0
    private var soundReverse = 0
    private var soundSkip = 0
    private var soundDraw4 = 0
    private var isMuted = false

    fun init(context: Context) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(attrs)
            .build()

        soundUno      = soundPool.load(context, R.raw.sound_uno, 1)
        soundCardPlay = soundPool.load(context, R.raw.sound_card_play, 1)
        soundCardDraw = soundPool.load(context, R.raw.sound_card_draw, 1)
        soundWin      = soundPool.load(context, R.raw.sound_win, 1)
        soundReverse  = soundPool.load(context, R.raw.sound_reverse, 1)
        soundSkip     = soundPool.load(context, R.raw.sound_skip, 1)
        soundDraw4    = soundPool.load(context, R.raw.sound_draw4, 1)
    }

    fun playUno()      { play(soundUno) }
    fun playCardPlay() { play(soundCardPlay) }
    fun playCardDraw() { play(soundCardDraw) }
    fun playWin()      { play(soundWin) }
    fun playReverse()  { play(soundReverse) }
    fun playSkip()     { play(soundSkip) }
    fun playDraw4()    { play(soundDraw4) }

    private fun play(soundId: Int) {
        if (!isMuted && soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        return isMuted
    }

    fun release() {
        soundPool.release()
    }
}
