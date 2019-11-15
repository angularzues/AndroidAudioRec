package com.graydog3.androidaudiorecorder

import android.app.Application

class MyApplication :Application(){
    companion object{
        var INSTANCE:MyApplication? = null
    }
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }
}