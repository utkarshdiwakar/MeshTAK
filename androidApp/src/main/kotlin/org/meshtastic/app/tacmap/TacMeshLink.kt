/*
 * Copyright (c) 2026 MeshTAK contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.meshtastic.app.tacmap

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.ServiceRepository
import soy.engindearing.omnitak.mobile.data.CoTEvent
import soy.engindearing.omnitak.mobile.data.CoTSource
import soy.engindearing.omnitak.mobile.data.CotXml
import soy.engindearing.omnitak.mobile.data.MeshNode
import soy.engindearing.omnitak.mobile.data.MeshPosition
import soy.engindearing.omnitak.mobile.data.MeshtasticCoTConverter
import soy.engindearing.omnitak.mobile.data.TakPacketV2Codec
import soy.engindearing.omnitak.mobile.domain.ContactStore
import soy.engindearing.omnitak.mobile.domain.MeshCoTRouter

/**
 * Phase 2 — the bridge between Meshtastic's data layer and the tactical map.
 *
 * Three inbound paths and one outbound path:
 *  - [NodeRepository.nodeDBbyNum] → CoT contacts (uid `MESHTASTIC-{HEX}`,
 *    the NodeCast/iOS parity scheme, converted via the ported
 *    [MeshtasticCoTConverter] so remarks/detail match those apps 1:1).
 *  - [PacketRepository.getWaypoints] → `b-m-p-w` waypoint markers (read-only).
 *  - [ServiceRepository.meshPacketFlow] filtered to portnum 78 → TAKPacketV2
 *    decode → markers/PLI from ATAK-speaking peers (NodeCast, other MeshTAK).
 *    Port 72 is deliberately not decoded here: the tacmap module carries only
 *    the V2 codec, and field logs show 2.7.x firmware delivers port-72
 *    payloads empty anyway; `:core:takserver` continues to own that port.
 *  - [send]: encode a tactical-map CoT event as port-78 TAKPacketV2 and
 *    broadcast it through [CommandSender] — the exact primitive
 *    `TAKMeshIntegration` uses (DataPacket to ^all, dataType 78, no ack).
 */
class TacMeshLink(
    private val contactStore: ContactStore,
    private val selfUidProvider: () -> String,
    private val locationProvider: soy.engindearing.omnitak.mobile.data.LocationProvider,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val serviceRepository: ServiceRepository,
    private val commandSender: CommandSender,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val log = Logger.withTag("TacMeshLink")

    /** uids this link ingested last pass, so vanished nodes/waypoints are removed. */
    private val liveNodeUids = mutableSetOf<String>()
    private val liveWaypointUids = mutableSetOf<String>()

    fun start() {
        // Radio node table → tactical-map contacts.
        scope.launch {
            nodeRepository.nodeDBbyNum.collect { table ->
                val ourNum = nodeRepository.ourNodeInfo.value?.num
                val fresh = mutableSetOf<String>()
                for ((num, node) in table) {
                    if (num == ourNum || node.isIgnored) continue
                    val event = node.toCoTEvent() ?: continue
                    fresh += event.uid
                    contactStore.ingest(event)
                }
                (liveNodeUids - fresh).forEach { contactStore.remove(it) }
                liveNodeUids.clear(); liveNodeUids += fresh
            }
        }

        // Meshtastic waypoints → b-m-p-w markers.
        scope.launch {
            packetRepository.getWaypoints().collect { packets ->
                val nowSecs = System.currentTimeMillis() / 1000
                val fresh = mutableSetOf<String>()
                for (p in packets) {
                    val wp = p.waypoint ?: continue
                    // Wire-generated proto: scalar fields are nullable.
                    val latI = wp.latitude_i ?: 0
                    val lonI = wp.longitude_i ?: 0
                    if (latI == 0 && lonI == 0) continue
                    val expire = wp.expire ?: 0
                    if (expire != 0 && expire != Int.MAX_VALUE && expire < nowSecs) continue
                    val uid = "WPT-${wp.id}"
                    val now = System.currentTimeMillis()
                    fresh += uid
                    contactStore.ingest(
                        CoTEvent(
                            uid = uid,
                            type = "b-m-p-w",
                            lat = latI * 1e-7,
                            lon = lonI * 1e-7,
                            callsign = wp.name.orEmpty().ifBlank { "Waypoint ${wp.id}" },
                            remarks = wp.description.orEmpty(),
                            timeIso = CotXml.isoMillis(now),
                            staleIso = CotXml.isoMillis(now + WAYPOINT_STALE_MS),
                            source = CoTSource.mesh("Meshtastic"),
                        ),
                    )
                }
                (liveWaypointUids - fresh).forEach { contactStore.remove(it) }
                liveWaypointUids.clear(); liveWaypointUids += fresh
            }
        }

        // Inbound port-78 TAKPacketV2 from ATAK-speaking peers.
        scope.launch {
            serviceRepository.meshPacketFlow
                .filter { it.decoded?.portnum?.value == TakPacketV2Codec.PORTNUM.toInt() }
                .collect { packet ->
                    val bytes = packet.decoded?.payload?.toByteArray() ?: return@collect
                    val event = TakPacketV2Codec.decode(bytes) ?: run {
                        log.w { "undecodable port-78 packet from ${packet.from} (${bytes.size}B)" }
                        return@collect
                    }
                    if (MeshCoTRouter.isSelfEcho(event, selfUidProvider())) return@collect
                    when (MeshCoTRouter.classify(event)) {
                        MeshCoTRouter.Destination.CONTACT -> {
                            contactStore.ingest(event.copy(source = CoTSource.mesh("Meshtastic")))
                            log.i { "RX TAKPacketV2 ${event.uid} type=${event.type} (${bytes.size}B)" }
                        }
                        // GeoChat from ATAK peers is not folded into Meshtastic's
                        // native chat DB (different message model); mesh text
                        // between MeshTAK users rides Meshtastic's own port-1 chat.
                        MeshCoTRouter.Destination.CHAT -> Unit
                    }
                }
        }
        // Phone-GPS PLI over port 78 — the NodeCast off-grid broadcaster's
        // role. Indispensable when the radios' own GPS has no fix (indoors):
        // node-table positions stay empty, so peers only see each other via
        // this phone-position broadcast. Callsign is the Meshtastic OWNER
        // long name (one identity across chat / node list / map); uid is the
        // stable per-install ANDROID-<uuid>, which peers dedupe on and the
        // self-echo filter drops when it bounces back.
        scope.launch {
            // Kick the fused-location engine in case the Map tab (whose
            // permission gate normally starts it) hasn't composed yet.
            // No-ops safely when location permission isn't granted yet.
            runCatching { locationProvider.start() }
            while (true) {
                kotlinx.coroutines.delay(PLI_INTERVAL_MS)
                val uid = selfUidProvider()
                if (uid.isBlank()) continue
                if (serviceRepository.connectionState.value != ConnectionState.Connected) continue
                val fix = locationProvider.effectiveFix() ?: continue
                val callsign = nodeRepository.ourNodeInfo.value?.user?.long_name
                    ?.takeIf { it.isNotBlank() } ?: "MeshTAK"
                val sent = send(
                    CoTEvent(
                        uid = uid,
                        type = "a-f-G-U-C",
                        lat = fix.lat,
                        lon = fix.lon,
                        hae = fix.altitudeM,
                        callsign = callsign,
                    ),
                )
                if (sent) log.d { "PLI sent — $callsign @ ${fix.lat},${fix.lon}" }
            }
        }
        log.i { "TacMeshLink collectors started" }
    }

    /** Outbound: tactical-map event → port-78 broadcast. */
    suspend fun send(event: CoTEvent): Boolean {
        if (serviceRepository.connectionState.value != ConnectionState.Connected) return false
        val payload = when {
            event.type == "b-t-f" -> TakPacketV2Codec.encodeChat(event)
            isTacticalMarker(event.type) -> TakPacketV2Codec.encodeMarker(event)
            else -> TakPacketV2Codec.encodePli(event)
        } ?: return false
        return runCatching {
            commandSender.sendData(
                DataPacket(
                    to = NodeAddress.ID_BROADCAST,
                    bytes = payload.toByteString(),
                    dataType = TakPacketV2Codec.PORTNUM.toInt(),
                    wantAck = false,
                ),
            )
            log.i { "TX TAKPacketV2 ${event.uid} type=${event.type} (${payload.size}B)" }
            true
        }.getOrElse {
            log.w { "port-78 send failed: ${it.message}" }
            false
        }
    }

    private fun Node.toCoTEvent(): CoTEvent? {
        val pos = validPosition ?: return null
        val meshNode = MeshNode(
            id = num.toLong() and 0xFFFFFFFFL,
            shortName = user.short_name,
            longName = user.long_name,
            position = MeshPosition(
                lat = latitude,
                lon = longitude,
                altitudeM = pos.altitude.takeIf { it != 0 },
            ),
            lastHeardEpoch = lastHeard.toLong(),
            snr = snr.takeIf { it != Float.MAX_VALUE }?.toDouble(),
            hopDistance = hopsAway.takeIf { it >= 0 },
            batteryLevel = batteryLevel,
        )
        return MeshtasticCoTConverter.nodeToCoT(meshNode)
            ?.copy(source = CoTSource.mesh("Meshtastic"))
    }

    companion object {
        private const val WAYPOINT_STALE_MS = 3_600_000L

        /** PLI cadence — 30 s, the ATAK convention NodeCast used; LoRa
         *  airtime stays negligible at this rate. */
        private const val PLI_INTERVAL_MS = 30_000L

        /** Marker CoT families that ride encodeMarker — mirrors NodeCast's
         *  MeshtasticManager.isTacticalMarker (bare PLI stays on encodePli). */
        fun isTacticalMarker(type: String): Boolean {
            if (type == "a-f-G-U-C") return false
            return type.startsWith("a-u-") ||
                type.startsWith("a-h-") ||
                type.startsWith("a-f-G-U-") ||
                type.startsWith("b-m-p-")
        }
    }
}
