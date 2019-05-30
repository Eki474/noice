package com.github.ashutoshgngwr.noice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.util.SparseArray
import androidx.core.util.set
import androidx.core.util.valueIterator
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment.Sound.Companion.LIBRARY
import kotlin.random.Random

class SoundManager(mediaPlayerService: Context) {

  class Playback(val sound: SoundLibraryFragment.Sound, val streamId: Int) {
    var volume: Float = 0.2f
    var timePeriod: Int = 60
    var isPlaying: Boolean = false
  }

  private val mSoundPool = SoundPool.Builder()
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
        .setUsage(AudioAttributes.USAGE_GAME)
        .build()
    )
    .setMaxStreams(LIBRARY.size())
    .build()

  private val mHandler = Handler()
  private val playbacks = SparseArray<Playback>()
  private val randomPlaybackCallbacks = SparseArray<Runnable>()
  private var playbackListener: OnPlaybackStateChangeListener? = null

  var isPlaying: Boolean = false

  init {
    for (sound in LIBRARY.valueIterator()) {
      playbacks[sound.resId] = Playback(
        sound,
        mSoundPool.load(mediaPlayerService, sound.resId, 1)
      )
    }
  }

  fun play(soundResId: Int) {
    isPlaying = true
    val playback = playbacks[soundResId]
    playback.isPlaying = true

    if (playback.sound.isLoopable) {
      mSoundPool.play(
        playback.streamId,
        playback.volume,
        playback.volume,
        1,
        -1,
        1.0f
      )
    } else {
      // non-loopable sounds should be played at random intervals in defined period
      randomPlaybackCallbacks[soundResId] = object : Runnable {
        override fun run() {
          if (isPlaying) {
            mSoundPool.play(
              playback.streamId,
              playback.volume,
              playback.volume,
              1,
              0,
              1.0f
            )
          }

          mHandler.postDelayed(this, Random.nextLong() % (playback.timePeriod * 1000))
        }
      }
      randomPlaybackCallbacks[soundResId].run()
    }

    notifyPlaybackStateChange()
  }

  private fun stop(playback: Playback) {
    playback.isPlaying = false
    mSoundPool.stop(playback.streamId)

    if (!playback.sound.isLoopable) {
      mHandler.removeCallbacks(randomPlaybackCallbacks[playback.sound.resId])
      randomPlaybackCallbacks.delete(playback.sound.resId)
    }
  }

  fun stop(soundResId: Int) {
    stop(playbacks[soundResId])

    // see if all playbacks are stopped
    var isPlaying = false
    for (p in playbacks.valueIterator()) {
      isPlaying = isPlaying || p.isPlaying
    }

    this.isPlaying = this.isPlaying && isPlaying
    notifyPlaybackStateChange()
  }

  fun stop() {
    isPlaying = false
    for (playback in playbacks.valueIterator()) {
      if (playback.isPlaying) {
        stop(playback)
      }
    }

    notifyPlaybackStateChange()
  }

  fun pausePlayback() {
    isPlaying = false
    mSoundPool.autoPause()
    notifyPlaybackStateChange()
  }

  fun resumePlayback() {
    isPlaying = true
    mSoundPool.autoResume()
    notifyPlaybackStateChange()
  }

  fun isPlaying(soundResId: Int): Boolean {
    return isPlaying && playbacks[soundResId].isPlaying
  }

  fun getVolume(soundResId: Int): Int {
    return Math.round(playbacks[soundResId].volume * 20)
  }

  fun setVolume(soundResId: Int, volume: Int) {
    val playback = playbacks[soundResId]
    playback.volume = volume / 20.0f
    mSoundPool.setVolume(playback.streamId, playback.volume, playback.volume)
  }

  fun getTimePeriod(soundResId: Int): Int {
    return playbacks[soundResId].timePeriod
  }

  fun setTimePeriod(soundResId: Int, timePeriod: Int) {
    playbacks[soundResId].timePeriod = timePeriod
  }

  fun release() {
    isPlaying = false
    mSoundPool.release()
    notifyPlaybackStateChange()
  }

  fun setOnPlaybackStateChangeListener(listener: OnPlaybackStateChangeListener?) {
    this.playbackListener = listener
  }

  private fun notifyPlaybackStateChange() {
    playbackListener?.onPlaybackStateChanged()
  }

  interface OnPlaybackStateChangeListener {
    fun onPlaybackStateChanged()
  }
}