package dev.rooni.aovo.ui.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import dev.rooni.aovo.data.ProfileIcons

fun profileIcon(key: String): ImageVector = when (ProfileIcons.normalise(key)) {
    "bolt" -> Icons.Filled.Bolt
    "rocket" -> Icons.Filled.RocketLaunch
    "turtle" -> Icons.Filled.HourglassBottom
    "hill" -> Icons.Filled.Landscape
    "route" -> Icons.Filled.Route
    "work" -> Icons.Filled.Work
    "home" -> Icons.Filled.Home
    "school" -> Icons.Filled.School
    "shop" -> Icons.Filled.ShoppingBag
    "lock" -> Icons.Filled.Lock
    "heart" -> Icons.Filled.Favorite
    "fire" -> Icons.Filled.LocalFireDepartment
    "battery" -> Icons.Filled.BatteryChargingFull
    "eco" -> Icons.Filled.Eco
    "sport" -> Icons.Filled.Speed
    "night" -> Icons.Filled.Nightlight
    "sun" -> Icons.Filled.WbSunny
    "rain" -> Icons.Filled.Umbrella
    "snow" -> Icons.Filled.AcUnit
    "city" -> Icons.Filled.LocationCity
    "offroad" -> Icons.Filled.Terrain
    "shield" -> Icons.Filled.Shield
    "star" -> Icons.Filled.Star
    else -> Icons.Filled.Bookmark
}
