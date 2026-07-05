package com.alcedo.studio.core

import android.os.Build

object ApiLevel {

    val current: Int get() = Build.VERSION.SDK_INT

    val isAtLeastO: Boolean get() = current >= Build.VERSION_CODES.O
    val isAtLeastO_MR1: Boolean get() = current >= Build.VERSION_CODES.O_MR1
    val isAtLeastP: Boolean get() = current >= Build.VERSION_CODES.P
    val isAtLeastQ: Boolean get() = current >= Build.VERSION_CODES.Q
    val isAtLeastR: Boolean get() = current >= Build.VERSION_CODES.R
    val isAtLeastS: Boolean get() = current >= Build.VERSION_CODES.S
    val isAtLeastS_V2: Boolean get() = current >= Build.VERSION_CODES.S_V2
    val isAtLeastT: Boolean get() = current >= Build.VERSION_CODES.TIRAMISU
    val isAtLeastU: Boolean get() = current >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    val isAtLeastV: Boolean get() = current >= 35
    val isAtLeastW: Boolean get() = current >= 36

    fun isAtLeast(level: Int): Boolean = current >= level

    fun isAtMost(level: Int): Boolean = current <= level

    fun isBetween(min: Int, max: Int): Boolean = current in min..max
}
