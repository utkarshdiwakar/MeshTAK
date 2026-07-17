/*
 * Copyright (c) 2026 MeshTAK contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package soy.engindearing.omnitak.mobile.host

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow
import soy.engindearing.omnitak.mobile.data.CoTEvent
import soy.engindearing.omnitak.mobile.data.DeviceHeadingProvider
import soy.engindearing.omnitak.mobile.data.KmlVectorOverlayStore
import soy.engindearing.omnitak.mobile.data.LocationProvider
import soy.engindearing.omnitak.mobile.data.MBTilesOverlayStore
import soy.engindearing.omnitak.mobile.data.RasterOverlayStore
import soy.engindearing.omnitak.mobile.data.TAKServer
import soy.engindearing.omnitak.mobile.data.UserPrefsStore
import soy.engindearing.omnitak.mobile.data.offline.OfflineRegionStore
import soy.engindearing.omnitak.mobile.domain.ConnectionState
import soy.engindearing.omnitak.mobile.domain.ContactStore
import soy.engindearing.omnitak.mobile.domain.DrawingStore
import soy.engindearing.omnitak.mobile.domain.LocalMarkerStore
import soy.engindearing.omnitak.mobile.domain.MapCameraStore

/**
 * Everything the ported tactical map (MapScreen + its sheets) needs from the
 * host application. Member names deliberately mirror the original
 * `OmniTAKApp` accessors so the ported source compiles with a one-line seam
 * change (`applicationContext as OmniTAKApp` → `LocalTacMapHost.current`).
 *
 * The host app (MeshTAK's androidApp) owns the concrete instances: the ported
 * stores are constructed as-is, while the TAK-server and mesh facades adapt
 * to the host's own transports (inert stubs until Phase 2 wires the mesh).
 */
interface TacMapHost {
    val contactStore: ContactStore
    val drawingStore: DrawingStore
    val localMarkerStore: LocalMarkerStore
    val mapCameraStore: MapCameraStore
    val userPrefsStore: UserPrefsStore
    val locationProvider: LocationProvider
    val headingProvider: DeviceHeadingProvider
    val kmlOverlayStore: KmlVectorOverlayStore
    val mbtilesOverlayStore: MBTilesOverlayStore
    val rasterOverlayStore: RasterOverlayStore
    val offlineRegionStore: OfflineRegionStore
    val serverManager: TakServerFacade
    val activeMeshManager: MeshSendFacade
}

/** TAK-server surface the map reads/sends through. Inert in a mesh-only
 *  host: flows stay at their empty defaults and [sendCoT] returns false. */
interface TakServerFacade {
    val activeServer: StateFlow<TAKServer?>
    val servers: StateFlow<List<TAKServer>>
    val connectedServerIds: StateFlow<Set<String>>
    val connectionState: StateFlow<ConnectionState>
    suspend fun sendCoT(xml: String, serverId: String? = null): Boolean
}

/** Outbound mesh hop for dropped markers / PLI / reports. Phase 2 routes
 *  this into the host's Meshtastic radio send (port-78 TAKPacketV2). */
interface MeshSendFacade {
    suspend fun sendCoTOverMesh(event: CoTEvent, channelIndex: UInt = 0u): Boolean
}

val LocalTacMapHost = staticCompositionLocalOf<TacMapHost> {
    error("TacMapHost not provided — wrap the tactical map in CompositionLocalProvider(LocalTacMapHost provides host)")
}
