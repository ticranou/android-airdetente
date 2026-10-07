package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.EfisHeadingSource
import com.airchecklists.app.di.ServiceLocator

private val BadgeBackground = Color(0xFF000000)
private val BadgeTextNormal = Color.White
private val BadgeTextCompensated = Color(0xFFFF9800)   // orange

@Composable
fun HeadingSourceBadge(modifier: Modifier = Modifier) {
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()

    val compensated = state.headingOffsetDeg != 0f
    val label = when (prefs.efisHeadingSource) {
        EfisHeadingSource.MAGNETIC -> if (compensated) "MAG+" else "MAG"
        EfisHeadingSource.GPS_TRACK -> "GPS"
    }
    val textColor = if (compensated && prefs.efisHeadingSource == EfisHeadingSource.MAGNETIC)
        BadgeTextCompensated else BadgeTextNormal

    Text(
        text = label,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(BadgeBackground, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
