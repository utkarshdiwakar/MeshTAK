package soy.engindearing.omnitak.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import soy.engindearing.omnitak.mobile.ui.theme.TacticalAccent

/**
 * PPLI-style readout card showing the operator's callsign and current
 * self-position telemetry. Mirrors the iOS card so both clients render
 * the same layout in the bottom-right corner of the map.
 *
 * Tap to collapse to a one-line callsign chip (closed-test feedback: the
 * full card covers too much map); tap again to expand. Collapse state
 * survives rotation via rememberSaveable but intentionally resets per
 * session — the Layers dialog toggle remains the way to hide it entirely.
 *
 * Values are caller-provided so we don't lock in a particular location
 * source; today the caller stubs sensible defaults, GAP-030b wires
 * `FusedLocationProviderClient` updates through.
 */
@Composable
fun SelfPositionCard(
    callsign: String,
    coordinateLabel: String,
    altitudeLabel: String,
    speedLabel: String,
    accuracyLabel: String,
    modifier: Modifier = Modifier,
) {
    var collapsed by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC000000))
            // The operator's own PPLI readout is friendly — frame it in the
            // brand accent (matching the callsign text), not hostile red
            // (red = hostile in TAK affiliation semantics).
            .border(2.dp, TacticalAccent, RoundedCornerShape(6.dp))
            .clickable { collapsed = !collapsed }
            .padding(horizontal = 10.dp, vertical = if (collapsed) 4.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            if (collapsed) "▸ $callsign" else "Callsign: $callsign",
            color = TacticalAccent,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        if (!collapsed) {
            Text(
                coordinateLabel,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                altitudeLabel,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "$speedLabel    $accuracyLabel",
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
