package com.app.queensgame.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.app.queensgame.core.Decree

/** A filled crown — Material has none, so it's drawn here. */
val CrownIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Crown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(3.2f, 17.5f)
        lineTo(2f, 7.2f)
        lineTo(7.6f, 11.4f)
        lineTo(12f, 4f)
        lineTo(16.4f, 11.4f)
        lineTo(22f, 7.2f)
        lineTo(20.8f, 17.5f)
        close()
        moveTo(3.4f, 19f)
        lineTo(20.6f, 19f)
        lineTo(20.6f, 21.4f)
        lineTo(3.4f, 21.4f)
        close()
    }.build()
}

val Decree.icon: ImageVector
    get() = when (this) {
        Decree.FOG_OF_WAR -> Icons.Filled.Cloud
        Decree.ROYAL_GUARD -> Icons.Filled.Shield
        Decree.ASSASSINS_RANGE -> Icons.Filled.GpsFixed
        Decree.COLOR_LOCK -> Icons.Filled.Lock
        Decree.TRUCE -> Icons.Filled.Flag
    }
