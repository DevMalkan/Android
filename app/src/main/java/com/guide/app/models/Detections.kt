package com.guide.app.models

data class Detection(
    val label: String,
    val conf: Float,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val distanceM: Float? = null
)

enum class ActionToken {
    CLEAR,
    STOP,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    CAUTION
}
