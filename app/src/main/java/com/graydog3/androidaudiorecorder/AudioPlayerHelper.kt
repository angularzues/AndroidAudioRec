package com.graydog3.androidaudiorecorder

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max

class AudioPlayerHelper {
    companion object {
        const val TAG: String = "AudioPlayerHelper"
        private var playerHelper: PlayerHolder? = null
        /**
         * @param sourcePath String 源文件路径，支持 url
         * @param withPath String? 伴奏文件路径，支持 url
         * @param sourceVolume Float 源文件播放音量
         * @param withVolume Float 伴奏播放音量
         * @param listener StateListener 播放状态监听
         * @param sourceNet Boolean 源文件是否为网络文件
         * @param withNet Boolean 伴奏是否为网络文件
         */
        @Synchronized
        fun startPlay(
            sourcePath: String,
            withPath: String? = null, sourceVolume: Float = 1.0f, withVolume: Float = 1.0f,
            listener: StateListener = object : StateListener {}
            , sourceNet: Boolean = false, withNet: Boolean = false
        ) {
            stopPlay()
            playerHelper =
                PlayerHolder(sourcePath, withPath, listener, sourceNet, withNet)
            playerHelper?.apply {
                setVolume(true, sourceVolume)
                setVolume(false, withVolume)
                start()
            }
        }

        fun stopPlay() {
            playerHelper?.apply {
                stop()
                playerHelper = null
            }
        }

        /**
         * 设置音量
         * @param isOrigin Boolean
         * @param volume Float，范围 0 — 1
         */
        fun setVolume(isOrigin: Boolean, volume: Float) {
            playerHelper?.apply {
                this.setVolume(isOrigin, volume)
            }
        }


        private fun generatePlayer(
            sourcePath: String?,
            listener: Player.EventListener,
            netSource: Boolean = false
        ): SimpleExoPlayer? {
            sourcePath?.apply {
                val defaultDataSourceFactory = DefaultDataSourceFactory(
                    MyApplication.INSTANCE,
                    "audio/mpeg"
                ) //  userAgent -> audio/mpeg  不能为空
                val extractorMediaSource = if (netSource) {
                    ExtractorMediaSource.Factory(defaultDataSourceFactory)
                        .createMediaSource(Uri.parse(sourcePath))
                } else {
                    ExtractorMediaSource.Factory(defaultDataSourceFactory)
                        .createMediaSource(Uri.fromFile(File(sourcePath)))
                }

                val source = ConcatenatingMediaSource() //创建一个媒体连接源
                val player =
                    ExoPlayerFactory.newSimpleInstance(
                        MyApplication.INSTANCE,
                        DefaultTrackSelector()
                    )
                source.addMediaSource(extractorMediaSource)
                player.addListener(listener)
                player.prepare(source)
                return player
            }
            return null
        }

    }


    class PlayerHolder(
        sourcePath: String,
        withPath: String?,
        val listener: StateListener,
        sourceNet: Boolean = false,
        withNet: Boolean = false
    ) {
        private val eventListener1 = object : Player.EventListener {
            override fun onPlayerError(error: ExoPlaybackException?) {
                listener.onError()
                error?.printStackTrace()
            }


            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    ExoPlayer.STATE_ENDED -> {
                        stop()
                    }
                }
            }
        }

        private val eventListener2: Player.EventListener = object : Player.EventListener {
            override fun onPlayerError(error: ExoPlaybackException?) {
                listener.onError()
                error?.printStackTrace()
            }


            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    ExoPlayer.STATE_ENDED -> {
                        //伴奏循环播放
                        player2?.apply {
                            seekTo(0)
                            this.playWhenReady = true
                        }
                    }
                }
            }
        }


        private val player1: SimpleExoPlayer? =
            generatePlayer(sourcePath, eventListener1, sourceNet)
        private val player2: SimpleExoPlayer? = withPath?.run {
            generatePlayer(withPath, eventListener2, withNet)
        }

        private val timingHandler: Handler = Handler(Looper.getMainLooper())
        private val timingRunnable: Runnable = object : Runnable {
            override fun run() {
                player1?.run {
//                    println("fuck duration $duration")
//                    println("fuck currentPosition $currentPosition")
                    val offset = max(0, duration) - currentPosition
                    if (offset >= 0) {
                        listener?.onTiming(TimeUnit.MILLISECONDS.toSeconds(offset))
                    }
                }
                timingHandler.postDelayed(this, 1000L)
            }
        }

        fun start() {
            player1?.apply {
                playWhenReady = true
            }
            player2?.apply {
                playWhenReady = true
            }
            listener?.onStart()
            timingHandler.postDelayed(timingRunnable, 1000L)
        }

        fun stop() {
            player1?.apply {
                playWhenReady = false
                stop()
                release()
            }
            player2?.apply {
                playWhenReady = false
                stop()
                release()
            }
            listener.onStop()
            timingHandler.removeCallbacksAndMessages(null)
        }

        fun setVolume(isOrigin: Boolean, volume: Float) {//设置音量大小
            if (isOrigin) {
                setVolume(player1, volume)
            } else {
                setVolume(player2, volume)
            }
        }

        private fun setVolume(player: SimpleExoPlayer?, volume: Float) {
            player?.apply {
                this.volume = volume
            }
        }
    }

    interface StateListener {
        fun onStart() {}
        fun onStop() {}
        fun onError() {}
        fun onTiming(restSecond: Long) {}
    }
}