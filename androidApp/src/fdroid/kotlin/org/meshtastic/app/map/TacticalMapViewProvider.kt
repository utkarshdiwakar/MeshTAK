/*
 * Copyright (c) 2026 MeshTAK contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.meshtastic.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import org.meshtastic.app.tacmap.MeshTakHostHolder
import org.meshtastic.core.ui.util.MapViewProvider
import soy.engindearing.omnitak.mobile.host.LocalTacMapHost
import soy.engindearing.omnitak.mobile.ui.screens.MapScreen
import soy.engindearing.omnitak.mobile.ui.theme.OmniTAKTheme

/**
 * MeshTAK: the NodeCast tactical map (MapLibre, MIL-STD-2525, CoT) mounted
 * as the Map tab, replacing the stock osmdroid view. The ported subtree
 * runs under its own theme + TacMapHost composition local.
 */
class TacticalMapViewProvider : MapViewProvider {
    @Composable
    override fun MapView(modifier: Modifier, navigateToNodeDetails: (Int) -> Unit, waypointId: Int?) {
        CompositionLocalProvider(LocalTacMapHost provides MeshTakHostHolder.host) {
            OmniTAKTheme {
                MapScreen()
            }
        }
    }
}
