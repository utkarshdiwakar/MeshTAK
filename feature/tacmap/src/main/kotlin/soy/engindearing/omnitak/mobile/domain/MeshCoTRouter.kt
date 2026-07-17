package soy.engindearing.omnitak.mobile.domain

import soy.engindearing.omnitak.mobile.data.CoTEvent

/**
 * Pure routing helpers for inbound mesh CoT events. Ported from NodeCast;
 * MeshTAK carries only the classify + self-echo core — the GeoChat→ChatMessage
 * threading half stays behind because MeshTAK's chat is Meshtastic's own.
 */
object MeshCoTRouter {

    /** Routing destination for an inbound mesh CoT event. */
    enum class Destination { CHAT, CONTACT }

    /**
     * Classify [event] into a routing destination.
     *  - "b-t-f" (GeoChat) → [Destination.CHAT]
     *  - everything else   → [Destination.CONTACT]
     */
    fun classify(event: CoTEvent): Destination =
        if (event.type == "b-t-f") Destination.CHAT else Destination.CONTACT

    /**
     * True when [event] is our own transmission echoed back off the mesh.
     * Matches on [selfUid] only (stable per-install `ANDROID-<uuid>`), plus
     * the `GeoChat.<selfUid>.` prefix chat uids carry — never on callsign,
     * which collides on every stock install's default.
     */
    fun isSelfEcho(event: CoTEvent, selfUid: String): Boolean =
        selfUid.isNotBlank() &&
            (event.uid == selfUid || event.uid.startsWith("GeoChat.$selfUid."))
}
