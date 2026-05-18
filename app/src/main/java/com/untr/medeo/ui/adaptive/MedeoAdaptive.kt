package com.untr.medeo.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MedeoWindowClass {
    Compact,
    Medium,
    Expanded;

    val usesWideLayout: Boolean
        get() = this != Compact
}

@Composable
fun rememberMedeoWindowClass(): MedeoWindowClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 840 -> MedeoWindowClass.Expanded
        widthDp >= 600 -> MedeoWindowClass.Medium
        else -> MedeoWindowClass.Compact
    }
}

fun MedeoWindowClass.pageMaxWidth(): Dp =
    when (this) {
        MedeoWindowClass.Compact -> Dp.Unspecified
        MedeoWindowClass.Medium -> 760.dp
        MedeoWindowClass.Expanded -> 1180.dp
    }

@Composable
fun AdaptiveWidthBox(
    windowClass: MedeoWindowClass,
    modifier: Modifier = Modifier,
    maxWidth: Dp = windowClass.pageMaxWidth(),
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = if (maxWidth == Dp.Unspecified) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxWidth)
            },
            content = content
        )
    }
}
