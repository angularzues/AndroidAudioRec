package com.graydog3.androidaudiorecorder

import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Environment
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.TimeUnit


class MediaRecordHelper {
    companion object {
        const val TAG: String = "AudioRecorderHelper"
        private const val SAMPLE_RATE = 44100//采样率
        private const val CHANNELS = 1 //声道数
        private const val BIT_RATE = 96000

        private var recordHandler: MediaRecordHandler? = null

        @Synchronized
        fun startRecord(
            listener: StateListener = object :
                StateListener {}
        ) {

            stopRecord()
            recordHandler = MediaRecordHandler(generateRecordAACFile(), listener)
            recordHandler!!.start()

        }


        fun stopRecord() {
            recordHandler?.apply {
                stop()
                recordHandler = null
            }

        }


        @Synchronized
        fun generateRecordAACFile(): String {
            try {
                val dir =
                    "${Environment.getExternalStorageDirectory().absolutePath}${File.separator}media_recorder${File.separator}"
                val dirFile = File(dir)
                if (!dirFile.exists()) {
                    dirFile.mkdirs()
                }
                var fileName = "audio_${System.currentTimeMillis()}.aac"
                var file = File("$dir${File.separator}$fileName")
                while (file.exists()) {
                    fileName = "audio_${System.currentTimeMillis()}.aac"
                    file = File("$dir${File.separator}$fileName")
                }
                return file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }


    }


    class MediaRecordHandler(
        private val filePath: String,
        private val stateListener: StateListener
    ) {
        private val mRecorder: MediaRecorder = MediaRecorder()
        private var startTime = 0L
        private val mAudioManager =
            (MyApplication.INSTANCE!!.getSystemService(AUDIO_SERVICE) as AudioManager)
        private var currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        private val maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        init {
            mRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            mRecorder!!.setOutputFile(filePath)
            mRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mRecorder!!.setAudioChannels(CHANNELS)
            mRecorder!!.setAudioSamplingRate(SAMPLE_RATE)
            mRecorder!!.setAudioEncodingBitRate(BIT_RATE)
            mRecorder!!.prepare()
        }


        private val timingHandler: Handler = Handler(Looper.getMainLooper())
        private val timingRunnable: Runnable = object : Runnable {
            override fun run() {
                mRecorder?.run {
                    stateListener?.onTiming(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime))
                }
                timingHandler.postDelayed(this, 1000L)
            }
        }

        fun start() {
            mAudioManager!!.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                maxVolume,
                AudioManager.FLAG_PLAY_SOUND
            )
            startTime = System.currentTimeMillis()
            mRecorder?.start()
            timingHandler.postDelayed(timingRunnable, 1000L)
        }

        fun stop() {
            mRecorder?.stop()
            stateListener.onStop(
                filePath,
                TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime)
            )
            mAudioManager!!.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                currentVolume,
                AudioManager.FLAG_PLAY_SOUND
            )
            timingHandler.removeCallbacksAndMessages(null)
        }

    }

    interface StateListener {
        fun onStop(filePath: String, duration: Long) {}
        fun onError() {}
        fun onTiming(restSecond: Long) {}
    }
}