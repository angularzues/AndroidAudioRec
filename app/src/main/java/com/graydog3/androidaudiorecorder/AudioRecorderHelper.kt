package com.graydog3.androidaudiorecorder

import android.media.*
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

class AudioRecorderHelper {

    companion object {
        const val TAG: String = "AudioRecorderHelper"
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = 1
        private const val BIT_RATE = 96000
        private const val MAX_BUFFER_SIZE = 8192

        private var recordHandler: RecordHandler? = null

        @Synchronized
        fun startRecord(
            listener: StateListener = object : StateListener {}
        ) {

            stopRecord()
            if (recordHandler == null) {
                recordHandler = RecordHandler(listener)
            }
            recordHandler!!.startRecord()

        }


        fun stopRecord() {
            recordHandler?.apply {
                stop()
                recordHandler = null
            }

        }

        private fun createAudioRecorder(): AudioRecord {
            return AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)
            )
        }

        @Synchronized
        private fun generateRecordAACFile(): String {
            try {
                val dir =
                    "${Environment.getExternalStorageDirectory().absolutePath}${File.separator}audio_record_aac${File.separator}"
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

        @Synchronized
        private fun generateRecordPCMFile(): String {
            try {
                val dir =
                    "${Environment.getExternalStorageDirectory().absolutePath}${File.separator}audio_record_pcm${File.separator}"
                val dirFile = File(dir)
                if (!dirFile.exists()) {
                    dirFile.mkdirs()
                }
                var fileName = "audio_${System.currentTimeMillis()}.pcm"
                var file = File("$dir${File.separator}$fileName")
                while (file.exists()) {
                    fileName = "audio_${System.currentTimeMillis()}.pcm"
                    file = File("$dir${File.separator}$fileName")
                }
                return file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }

    }

    private class RecordHandler(
        val stateListener: StateListener
    ) {

        private val filePath: String = generateRecordAACFile()
        private val fileBufferOutputStream: BufferedOutputStream
        private val audioRecorder: AudioRecord = createAudioRecorder()
        private val encoder: MediaCodec =
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        private val encodeInputBuffer: Array<ByteBuffer>
        private val encodeOutputBuffer: Array<ByteBuffer>
        private val bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
        private val queue: ArrayBlockingQueue<ByteArray>

        @Volatile
        private var isRecording = false

        init {
            val format =
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS)
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_BUFFER_SIZE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()  //等待录音数据
            encodeInputBuffer = encoder.inputBuffers
            encodeOutputBuffer = encoder.outputBuffers
            queue = ArrayBlockingQueue<ByteArray>(10)
            fileBufferOutputStream = BufferedOutputStream(FileOutputStream(File(filePath)))
        }


        fun startRecord() {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    //录音，数据放到队列里面
                    audioRecorder?.apply {
                        val mBuffer = ByteArray(2048)
                        isRecording = true
                        audioRecorder.startRecording()
                        while (isRecording) {
                            val read = audioRecorder.read(mBuffer, 0, 2048)
                            if (read > 0) {
                                val audio = ByteArray(read)
                                System.arraycopy(mBuffer, 0, audio, 0, read)
                                queue.put(audio) //将 PCM数据放入缓存队列
                            }
                        }

                    }
                } catch (e: Exception) {
                    //录音异常
                    GlobalScope.launch(Dispatchers.Main) {
                        stateListener?.onError()
                    }
                    e.printStackTrace()
                }
            }

            GlobalScope.launch(Dispatchers.IO) {
                try {
                    encoder?.apply {
                        encodeToAAC()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }

        }


        private fun encodeToAAC() {
            var inputIndex: Int
            var inputBuffer: ByteBuffer
            var outputIndex: Int
            var outputBuffer: ByteBuffer
            var chunkAudio: ByteArray
            var outBitSize: Int
            var outPacketSize: Int
            var chunkPCM: ByteArray?
            val pcmOutPutStream = BufferedOutputStream(FileOutputStream(generateRecordPCMFile()))
            while (isRecording || !queue.isEmpty()) {
                if (queue.isEmpty()) {
                    continue
                } else {
                    chunkPCM = queue.take()
                }
                //FIXME: 不需要输出 PCM
                pcmOutPutStream.write(chunkPCM)
                inputIndex = encoder.dequeueInputBuffer(-1)
                if (inputIndex >= 0) {
                    inputBuffer = encodeInputBuffer[inputIndex]
                    inputBuffer.clear()
                    inputBuffer.limit(chunkPCM.size)
                    inputBuffer.put(chunkPCM)//PCM数据填充给inputBuffer
                    encoder.queueInputBuffer(inputIndex, 0, chunkPCM.size, 0, 0)//通知编码器 编码
                }

                outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputIndex >= 0) {
                    outBitSize = bufferInfo.size
                    outPacketSize = outBitSize + 7//7为ADTS头部的大小
                    outputBuffer = encodeOutputBuffer[outputIndex]//拿到输出Buffer
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + outBitSize)
                    chunkAudio = ByteArray(outPacketSize)
                    addADTStoPacket(SAMPLE_RATE, chunkAudio, outPacketSize)//添加ADTS
                    outputBuffer.get(chunkAudio, 7, outBitSize)//将编码得到的AAC数据 取出到byte[]中 偏移量offset=7
                    outputBuffer.position(bufferInfo.offset)
                    try {
                        fileBufferOutputStream.write(
                            chunkAudio,
                            0,
                            chunkAudio.size
                        )//BufferOutputStream 将文件保存到内存卡中 *.aac
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    encoder.releaseOutputBuffer(outputIndex, false)
                    outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                }
            }
            //FIXME: 不需要输出 pcm
            pcmOutPutStream.flush()
            pcmOutPutStream.close()
            stopRecorder()
        }


        fun addADTStoPacket(sampleRateType: Int, packet: ByteArray, packetLen: Int) {
            val profile = 2 // AAC LC
            val chanCfg = 1 // CPE

            packet[0] = 0xFF.toByte()
            packet[1] = 0xF9.toByte()
            packet[2] = ((profile - 1 shl 6) + (sampleRateType shl 2) + (chanCfg shr 2)).toByte()
            packet[3] = ((chanCfg and 3 shl 6) + (packetLen shr 11)).toByte()
            packet[4] = (packetLen and 0x7FF shr 3).toByte()
            packet[5] = ((packetLen and 7 shl 5) + 0x1F).toByte()
            packet[6] = 0xFC.toByte()
        }


        private fun stopRecorder(): Boolean {
            try {
                fileBufferOutputStream.flush()
                audioRecorder?.apply {
                    stop()
                    release()
                }
                encoder?.apply {
                    stop()
                    release()
                }
                GlobalScope.launch(Dispatchers.Main) {
                    stateListener.onStop(filePath)
                }

            } catch (e: IOException) {
                e.printStackTrace()
                GlobalScope.launch(Dispatchers.Main) {
                    stateListener.onError()
                }
            } finally {
                try {
                    fileBufferOutputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            return true
        }

        /**
         * 给外界用于关闭录音
         */
        fun stop() {
            isRecording = false
        }

    }

    interface StateListener {
        fun onStop(filePath: String) {}
        fun onError() {}
    }

}