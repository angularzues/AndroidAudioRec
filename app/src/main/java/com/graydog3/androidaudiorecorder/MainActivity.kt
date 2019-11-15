package com.graydog3.androidaudiorecorder

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private var currentRecordPath = ""

    private val stateLiveData = MutableLiveData<RecorderState>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        stateLiveData.value = RecorderState.ORIGIN
        record_btn.setOnClickListener {
            //获取录音，文件读写权限，此处省略，在设置中开启
            when(stateLiveData.value){
                RecorderState.ORIGIN ->{
                    //用 MediaRecorder 录音并播放
                    MediaRecordHelper.startRecord(object : MediaRecordHelper.StateListener {
                        override fun onStop(filePath: String, duration: Long) {
                            currentRecordPath = filePath
                            stateLiveData.value = RecorderState.RECORD_FINISH
                        }

                        override fun onTiming(restSecond: Long) {
                            time_tips.text = "$restSecond S"
                        }
                    })
                    stateLiveData.value = RecorderState.RECORDING
                }
                RecorderState.RECORDING ->{
                   MediaRecordHelper.stopRecord()
                }
                RecorderState.RECORD_FINISH ->{
                    AudioPlayerHelper.startPlay(currentRecordPath,listener = object :AudioPlayerHelper.StateListener{
                        override fun onStop() {
                            stateLiveData.value = RecorderState.ORIGIN
                        }

                        override fun onTiming(restSecond: Long) {
                            time_tips.text = "$restSecond S"
                        }
                    })
                }
                RecorderState.PLAYING ->{
                    record_btn.text = "播放中"
                }
            }



        }
        stateLiveData.observe(this, Observer {
            when(it){
                RecorderState.ORIGIN ->{
                    record_btn.text = "点击录音"
                }
                RecorderState.RECORDING ->{
                    record_btn.text = "录音中"
                }
                RecorderState.RECORD_FINISH ->{
                    record_btn.text = "点击播放"
                }
                RecorderState.PLAYING ->{
                    record_btn.text = "播放中"
                }
            }
        })

    }

    enum class RecorderState {
        ORIGIN, RECORDING, RECORD_FINISH, PLAYING
    }
}
