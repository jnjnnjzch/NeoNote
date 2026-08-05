package com.neonote

import androidx.compose.foundation.layout.padding as composePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Keeps combined horizontal/bottom chrome spacing readable on the current Compose API. */
internal fun Modifier.padding(horizontal: Dp, bottom: Dp): Modifier =
    composePadding(start = horizontal, end = horizontal, bottom = bottom)
