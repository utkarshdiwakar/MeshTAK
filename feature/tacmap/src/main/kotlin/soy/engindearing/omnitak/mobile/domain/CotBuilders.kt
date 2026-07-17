package soy.engindearing.omnitak.mobile.domain

import soy.engindearing.omnitak.mobile.data.CoTEvent
import soy.engindearing.omnitak.mobile.data.CotXml
import java.util.UUID

/**
 * Plain-XML builders for the CoT subset OmniTAK ships. Kept separate
 * from CoTEvent (parse-side) so the send-side message shapes are one
 * obvious place to look. Envelope assembly + escaping live in
 * [CotXml]; this object owns the per-message-type detail blocks.
 */
object CotBuilders {

    /** ISO-8601 UTC timestamp for "now". */
    private fun nowIso(): String = CotXml.isoSeconds()

    /** ISO-8601 UTC timestamp for a given offset from now. */
    private fun isoOffset(seconds: Long): String =
        CotXml.isoSeconds(System.currentTimeMillis() + seconds * 1000L)

    /**
     * `t-x-d-d` "Tasking Delete Data" — the canonical TAK delete
     * primitive. Sent to the server, it propagates to other EUDs which
     * remove the target marker from their map. The deleter's own UID
     * goes in the event's `uid` field; the target UID lives on the
     * `<link>` element with `relation="p-p"`.
     */
    fun buildDeleteEvent(targetUid: String, senderUid: String): String {
        val now = nowIso()
        val detail = buildString {
            append("<detail>")
            append("<link uid=\"").append(CotXml.escape(targetUid)).append("\" relation=\"p-p\"/>")
            append("<__forcedelete/>")
            append("</detail>")
        }
        return CotXml.buildEvent(
            uid = senderUid,
            type = "t-x-d-d",
            how = "h-g-i-g-o",
            lat = 0.0, lon = 0.0, hae = 0.0,
            timeIso = now,
            staleIso = isoOffset(60),
            detailXml = detail,
        )
    }

    /**
     * Resurrect a CoTEvent as a fresh CoT XML string. Used by the
     * "Send to Contacts" path so we can re-broadcast the selection to
     * a specific recipient by adding `<dest uid="..."/>` to its detail.
     * Falls back to event.rawXml when present (preserves any extension
     * detail elements like <chat>, <usericon>, mil-std symbology).
     */
    fun rebuildEvent(event: CoTEvent, destUids: List<String>): String {
        // If the original parsed XML is around, re-wrap it with the
        // injected <dest> elements. Otherwise synthesize a minimal CoT.
        val now = event.timeIso ?: nowIso()
        val stale = event.staleIso ?: isoOffset(120)
        val detail = buildString {
            append("<detail>")
            append("<contact")
            event.callsign?.let { append(" callsign=\"").append(CotXml.escape(it)).append('"') }
            append("/>")
            // ATAK / iTAK render FEMA markers (and any custom-glyph marker)
            // off the `iconsetpath` detail — emit it when present so peers
            // with the catalog show the right symbol (#29). Spot Map points
            // (#98) additionally carry their swatch in <color argb>; ATAK reads
            // the dot colour from there, not the CoT type.
            event.iconsetPath?.takeIf { it.isNotBlank() }?.let {
                append("<usericon iconsetpath=\"").append(CotXml.escape(it)).append("\"/>")
            }
            event.colorArgb?.let {
                append("<color argb=\"").append(it).append("\"/>")
            }
            if (event.remarks.isNotBlank()) {
                append("<remarks>").append(CotXml.escape(event.remarks)).append("</remarks>")
            }
            event.courseHeading?.let {
                append("<track course=\"").append(it).append("\" speed=\"0.0\"/>")
            }
            destUids.forEach { append("<dest uid=\"").append(CotXml.escape(it)).append("\"/>") }
            append("</detail>")
        }
        return CotXml.buildEvent(
            uid = event.uid,
            type = event.type,
            how = "h-g-i-g-o",
            lat = event.lat, lon = event.lon,
            hae = event.hae, ce = event.ce, le = event.le,
            timeIso = now,
            staleIso = stale,
            detailXml = detail,
        )
    }


    /** Fresh random TAK-style UID. */
    fun newUid(): String = UUID.randomUUID().toString()

    /** XML escape — delegates to the shared [CotXml.escape]. Kept as a
     *  public alias because non-CoT XML emitters (KML/data-package
     *  exporters) call it too. */
    fun xmlEscape(s: String): String = CotXml.escape(s)
}
