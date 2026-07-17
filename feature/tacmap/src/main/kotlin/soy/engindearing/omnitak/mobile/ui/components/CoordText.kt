package soy.engindearing.omnitak.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import soy.engindearing.omnitak.mobile.data.CoordFormat
import soy.engindearing.omnitak.mobile.data.CoordFormatter
import soy.engindearing.omnitak.mobile.host.LocalTacMapHost

/**
 * Format a coordinate pair honoring the operator's Settings coordinate
 * format (decimal / DMS / MGRS / UTM / TWD97) — issues #3/#4. Sheets and
 * panels used to hardcode `"%.5f, %.5f"`, silently re-introducing the
 * "settings picker is a no-op" regression for MGRS/TWD97 operators.
 *
 * MeshTAK: reads the pref via [LocalTacMapHost] (was the OmniTAKApp
 * application-context cast in NodeCast).
 */
@Composable
fun rememberCoordText(lat: Double, lon: Double): String {
    val host = LocalTacMapHost.current
    val format by remember(host) {
        host.userPrefsStore.prefs.map { it.coordFormat }
    }.collectAsState(initial = CoordFormat.LATLON_DECIMAL)
    return CoordFormatter.position(lat, lon, format)
}
