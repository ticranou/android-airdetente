package com.airchecklists.app.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import kotlinx.coroutines.delay

/**
 * Startup splash: the club logo on a white circular backdrop (so its black text
 * stays legible in both light and dark themes), centered on the theme background.
 * Fades in, holds, then calls [onFinished].
 */
@Composable
fun SplashScreen(durationSeconds: Int, onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "splashAlpha",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay((durationSeconds.coerceAtLeast(1)) * 1000L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .alpha(alpha)
                .size(220.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_air_detente),
                contentDescription = "Air détente",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
