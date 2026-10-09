package com.brandon.videodownloader.ui

import java.util.Locale

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024; i++
    }
    return if (i == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[i])
}

fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatResolution(width: Int?, height: Int?): String {
    if (height == null) return ""
    // Para videos verticales (TikTok/Reels) la "calidad" la da el lado corto.
    val shortSide = if (width != null && width < height) width else height
    return "${shortSide}p"
}
