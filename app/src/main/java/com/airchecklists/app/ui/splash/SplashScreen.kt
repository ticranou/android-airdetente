package com.airchecklists.app.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import kotlinx.coroutines.delay

/** Background matching the splash artwork so the image blends into the screen edges. */
private val SplashBackground = Color(0xFF282828)

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
            .background(SplashBackground),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.splash_screen),
            contentDescription = "Air Détente",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .alpha(alpha)
                .fillMaxSize()
                .padding(32.dp),
        )
    }
}
