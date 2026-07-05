package com.pixelfruit.ui

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * PixelFruit 滤镜编辑 Activity — P-07 滤镜预设 / P-04 直方图 / P-05 面部美白
 * 由 AlcedoStudio 相册通过 Intent 唤起（A-04 跨模块链路）
 *
 * 已知不足：
 * - P-02: 尼康 Z8/Z9 NEF 无法解析
 * - P-03: iPhone ProRAW 大部分无法打开
 * - P-09: 大图处理需 Coroutine+C++ 原生线程对标，避免主线程卡顿
 */
class PixelFruitActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Compose UI — 滤镜预设选择 / 直方图实时显示 / 面部美白面板
        // 当前骨架：Activity 可被唤起，返回基础结果
        setResult(RESULT_OK)
        finish()
    }
}