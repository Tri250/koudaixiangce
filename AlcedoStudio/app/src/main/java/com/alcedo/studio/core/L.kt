package com.alcedo.studio.core

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object L {

    private const val TAG_PREFIX = "AlcedoStudio"
    private val isDebug = AtomicBoolean(BuildConfig.DEBUG)

    @JvmStatic
    fun d(tag: String, message: String) {
        if (isDebug.get()) {
            Log.d("$TAG_PREFIX.$tag", message)
        }
    }

    @JvmStatic
    fun d(tag: String, message: String, throwable: Throwable) {
        if (isDebug.get()) {
            Log.d("$TAG_PREFIX.$tag", message, throwable)
        }
    }

    @JvmStatic
    fun e(tag: String, message: String) {
        Log.e("$TAG_PREFIX.$tag", message)
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e("$TAG_PREFIX.$tag", message, throwable)
    }

    @JvmStatic
    fun w(tag: String, message: String) {
        if (isDebug.get()) {
            Log.w("$TAG_PREFIX.$tag", message)
        }
    }

    @JvmStatic
    fun w(tag: String, message: String, throwable: Throwable) {
        if (isDebug.get()) {
            Log.w("$TAG_PREFIX.$tag", message, throwable)
        }
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        if (isDebug.get()) {
            Log.i("$TAG_PREFIX.$tag", message)
        }
    }

    @JvmStatic
    fun v(tag: String, message: String) {
        if (isDebug.get()) {
            Log.v("$TAG_PREFIX.$tag", message)
        }
    }

    fun setDebug(debug: Boolean) {
        isDebug.set(debug)
    }

    fun getDebug(): Boolean = isDebug.get()
}
