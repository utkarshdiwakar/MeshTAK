/*
 * Copyright (c) 2026 MeshTAK contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.meshtastic.app.tacmap

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
import soy.engindearing.omnitak.mobile.host.MeshSendFacade
import soy.engindearing.omnitak.mobile.host.TacMapHost
import soy.engindearing.omnitak.mobile.host.TakServerFacade

/**
 * MeshTAK's implementation of the tactical map's host contract: the ported
 * stores are constructed as-is; the TAK-server facade is inert (mesh-only
 * app), and the mesh facade is a Phase-2 seam that will route through the
 * Meshtastic radio send (port-78 TAKPacketV2).
 */
class MeshTakHost(app: Application) : TacMapHost {
    override val userPrefsStore = UserPrefsStore(app)
    override val contactStore = ContactStore()
    override val drawingStore = DrawingStore()
    override val localMarkerStore = LocalMarkerStore(app)
    override val mapCameraStore = MapCameraStore(userPrefsStore)
    override val locationProvider = LocationProvider(app)
    override val headingProvider = DeviceHeadingProvider(app)
    override val kmlOverlayStore = KmlVectorOverlayStore(app)
    override val mbtilesOverlayStore = MBTilesOverlayStore(app)
    override val rasterOverlayStore = RasterOverlayStore(app)
    override val offlineRegionStore = OfflineRegionStore(app)

    override val serverManager: TakServerFacade = object : TakServerFacade {
        override val activeServer: StateFlow<TAKServer?> = MutableStateFlow(null)
        override val servers: StateFlow<List<TAKServer>> = MutableStateFlow(emptyList())
        override val connectedServerIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Disconnected)
        override suspend fun sendCoT(xml: String, serverId: String?): Boolean = false
    }

    /** Phase 2 — the live mesh link. Null until [startMeshLink] runs (which
     *  must be AFTER startKoin; this host is constructed before Koin). */
    @Volatile private var meshLink: TacMeshLink? = null
    @Volatile private var selfUidCache: String = ""
    private val hostScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    )

    override val activeMeshManager: MeshSendFacade = object : MeshSendFacade {
        override suspend fun sendCoTOverMesh(event: CoTEvent, channelIndex: UInt): Boolean =
            meshLink?.send(event) ?: false
    }

    /** Resolve the Meshtastic repositories from Koin and start the bridges
     *  (nodes → contacts, waypoints → markers, port-78 RX). Idempotent. */
    fun startMeshLink() {
        if (meshLink != null) return
        // Mint + cache the stable self uid (ANDROID-<uuid>) so the RX
        // self-echo filter has a non-suspending identity to compare against.
        hostScope.launch {
            userPrefsStore.ensureSelfUid()
            userPrefsStore.prefs.collect { selfUidCache = it.selfUid }
        }
        val koin = org.koin.core.context.GlobalContext.get()
        meshLink = TacMeshLink(
            contactStore = contactStore,
            selfUidProvider = { selfUidCache },
            nodeRepository = koin.get(),
            packetRepository = koin.get(),
            serviceRepository = koin.get(),
            commandSender = koin.get(),
        ).also { it.start() }
    }
}

/** Process-wide holder — initialized once from Application.onCreate. */
object MeshTakHostHolder {
    @Volatile lateinit var host: MeshTakHost
        private set

    fun init(app: Application) {
        if (::host.isInitialized) return
        host = MeshTakHost(app)
    }
}
