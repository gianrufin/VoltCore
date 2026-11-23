package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.TelemetrySnapshotEntity
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.PureWhite

@Composable
fun MicroSuggestionsCard(
    snapshot: TelemetrySnapshotEntity,
    modifier: Modifier = Modifier
) {
    val suggestion = rememberSuggestion(snapshot)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("micro_suggestions_card"),
        shape = MaterialTheme.shapes.medium,
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = EmeraldDim,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = ElectricEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart tip",
                    style = MonoLabelSmall,
                    color = ElectricEmerald
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite
                )
            }
        }
    }
}

private fun rememberSuggestion(snapshot: TelemetrySnapshotEntity): String {
    val soc = snapshot.exactSocPercent
    val temp = snapshot.temperatureCelsius
    val current = snapshot.currentAmps

    return when {
        temp <= 2.0f && current > 0.1f -> "Sub-zero charge warning: LiFePO4 charging below 0°C risks permanent lithium plating. Heat pack before charging."
        soc in 70f..85f -> "Storage sweet spot: Pack is at optimal 70-85% state of charge for long-term resting longevity."
        soc <= 15f -> "Low battery alert: Recharge soon to prevent deep discharge voltage sag under heavy inverter loads."
        current > 15f -> "High charge current detected: Ensure alternator DC-DC charger and wire gauge are rated for continuous load."
        temp >= 40f -> "Thermal note: Pack temperature is elevated. High ambient heat reduces calendar life."
        else -> "Pack telemetry nominal. Cell voltage balance and shunt calibration operating normally."
    }
}
