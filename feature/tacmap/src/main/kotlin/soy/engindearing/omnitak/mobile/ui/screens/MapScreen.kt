package soy.engindearing.omnitak.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import soy.engindearing.omnitak.mobile.ui.theme.TacticalAccent
import soy.engindearing.omnitak.mobile.ui.theme.TacticalBackground
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import soy.engindearing.omnitak.mobile.data.CoTAffiliation
import soy.engindearing.omnitak.mobile.host.LocalTacMapHost
import soy.engindearing.omnitak.mobile.data.CoTEvent
import soy.engindearing.omnitak.mobile.data.Drawing
import soy.engindearing.omnitak.mobile.data.DrawingKind
import soy.engindearing.omnitak.mobile.data.GeoMath
import soy.engindearing.omnitak.mobile.domain.ConnectionState
import soy.engindearing.omnitak.mobile.i18n.Loc
import soy.engindearing.omnitak.mobile.ui.components.ATAKStatusBar
import soy.engindearing.omnitak.mobile.ui.components.CompassOverlay
import soy.engindearing.omnitak.mobile.ui.components.ContactsPanel
import soy.engindearing.omnitak.mobile.ui.components.LayersDialog
import soy.engindearing.omnitak.mobile.ui.components.MarkerEditSheet
import soy.engindearing.omnitak.mobile.ui.components.RadialAction
import soy.engindearing.omnitak.mobile.ui.components.RadialMenu
import soy.engindearing.omnitak.mobile.ui.components.SelfPositionCard
import soy.engindearing.omnitak.mobile.ui.components.TacticalMap
import soy.engindearing.omnitak.mobile.ui.components.styleJsonForProvider
import soy.engindearing.omnitak.mobile.ui.components.ToolEntry
import soy.engindearing.omnitak.mobile.ui.components.ToolsDrawer
import soy.engindearing.omnitak.mobile.ui.components.rememberLocationPermission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Cold-start fallback when there is no persisted camera and no GPS fix yet.
// World-level zoom centered on 0°N 0°E — neutral for every operator globally.
private val FALLBACK_GLOBAL_VIEW = LatLng(0.0, 0.0)
private const val FALLBACK_GLOBAL_ZOOM = 2.0

// Issue #76 — screen-space tolerance for tapping a drawing's outline/rim.
// ~24px ≈ finger-tip slack on a mid-density device; lines/rims are thin so
// this keeps a 3px stroke comfortably grabbable.
private const val DRAWING_TAP_TOLERANCE_PX = 24f

// Issue #76 — if a contact is within this screen radius of the tap, the
// drawing hit-test defers so the marker's edit sheet wins. Matches
// TacticalMap.TAP_HIT_RADIUS_PX (the contact hit radius).
private const val CONTACT_TAP_DEFER_PX = 72.0

@Composable
fun MapScreen(onOpenTab: (String) -> Unit = {}) {
    // MeshTAK seam: the host app provides everything the map needs via
    // TacMapHost (was `applicationContext as OmniTAKApp` in NodeCast).
    val app = LocalTacMapHost.current
    // Issue #89 — status-bar inset in dp. enableEdgeToEdge lets content draw
    // under the system bars; overlays anchored near the top (compass, MapLibre
    // built-in compass) add this so they clear the shifted ATAKStatusBar.
    val statusBarTopInsetDp = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    // #182 — landscape is the plate-carrier orientation (Pixel 9/10 Pro) and
    // has little vertical room. The map chrome (tools drawer, zoom/center
    // control stack) lays out compactly there so it doesn't eat the short map.
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val active by app.serverManager.activeServer.collectAsState()
    val connState by app.serverManager.connectionState.collectAsState()
    val allServers by app.serverManager.servers.collectAsState()
    val connectedIds by app.serverManager.connectedServerIds.collectAsState()
    val contacts by app.contactStore.contacts.collectAsState()
    // Layers toggle: mesh-origin contacts are persisted because the
    // operator's last choice should survive a process restart. Default
    // visible — matches iOS.
    val userPrefs by app.userPrefsStore.prefs.collectAsState(initial = soy.engindearing.omnitak.mobile.data.UserPrefs())
    val meshNodesVisible = userPrefs.meshNodesLayerVisible
    // GAP-110 — persisted UI toggles. These survive relaunch instead of
    // resetting to defaults each time the user opens the app. Aliases so
    // existing read sites stay terse.
    val gridEnabled = userPrefs.gridEnabled
    val drawingsVisible = userPrefs.drawingsVisible
    val aircraftVisible = userPrefs.aircraftVisible
    val contactsVisible = userPrefs.contactsVisible
    val callsignCardVisible = userPrefs.callsignCardVisible
    val followMeActive = userPrefs.followMeActive
    val map3dEnabled = userPrefs.map3dEnabled
    val prefScope = rememberCoroutineScope()
    fun mutatePref(block: (soy.engindearing.omnitak.mobile.data.UserPrefs) -> soy.engindearing.omnitak.mobile.data.UserPrefs) {
        prefScope.launch { app.userPrefsStore.update(block) }
    }

    val locationGranted by rememberLocationPermission()
    // GAP-030b — start the FusedLocationProviderClient as soon as we have
    // permission. Idempotent, safe to re-invoke on every recomposition.
    val selfFix by app.locationProvider.fix.collectAsState()
    LaunchedEffect(locationGranted) {
        if (locationGranted) app.locationProvider.start()
    }
    var radialAnchor by remember { mutableStateOf<Offset?>(null) }
    var radialLatLng by remember { mutableStateOf<LatLng?>(null) }
    var markerSheetLatLng by remember { mutableStateOf<LatLng?>(null) }
    // #29 — FEMA / IC marker palette. Tap a FEMA action → camera-center is
    // captured here → palette sheet picks an icon → drop emits the CoT
    // event with the FEMA cotType + iconsetpath. Independent of the
    // generic markerSheetLatLng pipeline so the affiliation-based UI
    // doesn't have to know about FEMA-specific fields.
    var femaPaletteLatLng by remember { mutableStateOf<LatLng?>(null) }
    var editingMarker by remember { mutableStateOf<CoTEvent?>(null) }
    var selfMarkerEditOpen by remember { mutableStateOf(false) }
    // #82 — manual self-position override. Stored on LocationProvider (not
    // local state) so it's the single source of truth: the puck + the
    // SelfPositionCard render it AND SelfPositionBroadcaster broadcasts it in
    // PPLI, so teammates see the operator where they placed themselves instead
    // of at live GPS. Collecting the flow keeps the map in lockstep with what
    // goes on the wire; null = live GPS (the "resume GPS" state).
    val manualSelfFix by app.locationProvider.manualFix.collectAsState()
    val effectiveSelfFix: soy.engindearing.omnitak.mobile.data.SelfFix? = manualSelfFix ?: selfFix
    var recenterTick by remember { mutableStateOf(0) }
    var zoomInTick by remember { mutableStateOf(0) }
    var zoomOutTick by remember { mutableStateOf(0) }
    // Issue #95/#96 — snap-to-north trigger (compass tap when lock is off)
    // and live bearing for the compass overlay.
    var snapNorthTick by remember { mutableStateOf(0) }
    var currentBearing by remember { mutableStateOf(0.0) }
    var measurementActive by remember { mutableStateOf(false) }
    // MeshTAK: UAS/mission/drone subsystems are not ported — the related
    // state, overlays, pills, and sheets from NodeCast are removed wholesale.
    var measurementPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    // #152 — range-rings tool: tap sets the center, concentric rings draw
    // around it via the native drawing renderer (RangeBearing.ringPoints).
    var rangeRingsActive by remember { mutableStateOf(false) }
    var rangeRingCenter by remember { mutableStateOf<LatLng?>(null) }
    var drawingKind by remember { mutableStateOf<DrawingKind?>(null) }
    var drawingPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var drawingPickerOpen by remember { mutableStateOf(false) }
    // Issue #76 — tap-to-select a placed drawing for edit/move/delete.
    // selectedDrawingId holds the tapped shape (drives the DrawingEditSheet);
    // movingDrawingId, when set, flips the map into drag-to-reposition mode.
    var selectedDrawingId by remember { mutableStateOf<String?>(null) }
    var movingDrawingId by remember { mutableStateOf<String?>(null) }
    // GAP-110 — gridEnabled, drawingsVisible, aircraftVisible, contactsVisible,
    // callsignCardVisible, followMeActive are now read from userPrefs (above)
    // so they survive relaunch.
    var layersSheetOpen by remember { mutableStateOf(false) }
    // #120 — offline map download sheet.
    var offlineMapsOpen by remember { mutableStateOf(false) }
    // #154 — military report entry sheet (MEDEVAC / SALUTE / SPOTREP).
    var reportsSheetOpen by remember { mutableStateOf(false) }
    val offlineRegions by app.offlineRegionStore.regions.collectAsState()
    var teamsPanelOpen by remember { mutableStateOf(false) }
    var panTarget by remember { mutableStateOf<LatLng?>(null) }
    var panTargetTick by remember { mutableStateOf(0) }
    // "Go to Coordinate" sheet (TWD97 / MGRS / Lat-Lon). Opened from the
    // Tools popup via CoordinateEntryEvents; reference coord = map centre
    // so 5+5 TWD97 grid input recovers the right 100 km cell.
    var coordEntryOpen by remember { mutableStateOf(false) }
    val coordEntryGen by soy.engindearing.omnitak.mobile.ui.components.CoordinateEntryEvents.openGeneration.collectAsState()
    LaunchedEffect(coordEntryGen) {
        if (coordEntryGen > 0L) coordEntryOpen = true
    }
    val drawings by app.drawingStore.drawings.collectAsState()
    // KML vector overlays (imported KML/KMZ rendered as GeoJSON sources).
    val kmlOverlays by app.kmlOverlayStore.overlays.collectAsState()
    // MBTiles raster tile overlays (served by the in-app tile server).
    val mbtilesOverlays by app.mbtilesOverlayStore.overlays.collectAsState()
    // Single-image raster overlays (GroundOverlay etc.) via ImageSource.
    val rasterImagery by app.rasterOverlayStore.overlays.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Issue #16 — Lasso freehand multi-select.
    // The MapLibreMap reference is captured via TacticalMap.onMapReady
    // so the lasso overlay can project screen pixels to LatLng on
    // drag end. lassoMode is flipped on by the Tools popup → service
    // event; it flips back automatically when LassoOverlay reports
    // the gesture completed (commits selection on the service).
    var lassoMode by remember { mutableStateOf(false) }
    var mapboxMap by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    val lassoService = remember { soy.engindearing.omnitak.mobile.domain.LassoSelectionService.shared }
    val lassoSelection by lassoService.current.collectAsState()
    var lassoActionsOpen by remember { mutableStateOf(false) }
    var contactPickerOpen by remember { mutableStateOf(false) }
    val appContext = LocalContext.current.applicationContext
    // Observe activation requests via a generation counter — every
    // increment means "user picked Lasso Select again." We track the
    // last value we honored so we only flip lassoMode on a fresh
    // edge. StateFlow keeps the latest value alive across MapScreen
    // recompositions, so an emission that lands before the collector
    // subscribed still gets applied as soon as we observe it.
    val activationGen by lassoService.activationGeneration.collectAsState()
    var lastHonoredGen by remember { mutableStateOf(activationGen) }
    LaunchedEffect(activationGen) {
        if (activationGen != lastHonoredGen) {
            lastHonoredGen = activationGen
            // Skip the initial "0" baseline — only react to real increments.
            if (activationGen > 0L) lassoMode = true
        }
    }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg, withDismissAction = true) }
    }

    // The Cesium globe renders only contacts + self — measurement,
    // drawings and lasso projection live on the MapLibre engine.
    // Activating one of those tools while the globe is up auto-drops to 2D
    // (the KML zoom handlers' proven pattern, and the iOS precedent) instead
    // of letting the tool silently no-op — the documented VC77 "dead buttons
    // on the globe" bug class.
    //
    // ADS-B is NOT in this guard: as a plugin its map overlay self-limits to
    // MapLibre (the handle is null on the globe → it no-ops), exactly as the
    // pre-plugin code never painted aircraft on the globe. So enabling ADS-B
    // does not force a drop to 2D — identical to today's behavior.
    LaunchedEffect(measurementActive, drawingKind, lassoMode, rangeRingsActive) {
        if (!userPrefs.cesiumGlobeEnabled) return@LaunchedEffect
        if (measurementActive || drawingKind != null || lassoMode || rangeRingsActive) {
            app.userPrefsStore.update { it.copy(cesiumGlobeEnabled = false) }
            toast(Loc.t("map.toast.globeTo2d"))
        }
    }
    // When the globe takes over, TacticalMap leaves composition and its
    // MapView is destroyed — drop the stale handle so auto-follow,
    // center-on-drone and lasso projection no-op cleanly instead of
    // driving a destroyed map. TacticalMap.onMapReady repopulates it
    // when the 2D engine comes back.
    LaunchedEffect(userPrefs.cesiumGlobeEnabled) {
        if (userPrefs.cesiumGlobeEnabled) mapboxMap = null
    }

    // Re-apply KML overlays to the live style whenever the set changes.
    // (Re-application after a style RELOAD is handled by TacticalMap.onStyleReady.)
    LaunchedEffect(kmlOverlays) {
        mapboxMap?.getStyle { style ->
            soy.engindearing.omnitak.mobile.ui.components.KmlOverlayRenderer
                .apply(style, kmlOverlays, app.kmlOverlayStore)
        }
    }
    // KML POINT placemarks render as native markers (addMarker) — the GeoJSON
    // SymbolLayer/CircleLayer path silently fails to paint on a subset of GL
    // drivers (see project_omnitak_android_marker_gpu_bug). Keyed on mapboxMap
    // too, so markers add as soon as the map is ready (not just on overlay change).
    LaunchedEffect(kmlOverlays, mapboxMap) {
        mapboxMap?.let { m ->
            soy.engindearing.omnitak.mobile.ui.components.KmlMarkerRenderer
                .apply(m, appContext, kmlOverlays, app.kmlOverlayStore)
        }
    }
    // KML lines/polygons render as native annotations (addPolyline/addPolygon),
    // same GL-paint workaround as points — the GeoJSON Line/Fill path is invisible
    // on Adreno/Mali/emulator (see KmlShapeRenderer, project_omnitak_android_marker_gpu_bug).
    LaunchedEffect(kmlOverlays, mapboxMap) {
        mapboxMap?.let { m ->
            soy.engindearing.omnitak.mobile.ui.components.KmlShapeRenderer
                .apply(m, kmlOverlays, app.kmlOverlayStore)
        }
    }
    // Re-apply MBTiles raster overlays when the set changes.
    LaunchedEffect(mbtilesOverlays) {
        mapboxMap?.getStyle { style ->
            soy.engindearing.omnitak.mobile.ui.components.KmlOverlayRenderer
                .applyMBTiles(style, mbtilesOverlays, app.mbtilesOverlayStore)
        }
    }
    // Re-apply single-image raster overlays when the set changes.
    LaunchedEffect(rasterImagery) {
        mapboxMap?.getStyle { style ->
            soy.engindearing.omnitak.mobile.ui.components.KmlOverlayRenderer
                .applyRaster(style, rasterImagery, app.rasterOverlayStore)
        }
    }
    // #120 — re-apply downloaded offline regions (cached MBTiles) when the
    // set changes. OfflineTilePolicy decides whether cache wins (offline) or
    // simply layers in (online). Device-pending: the on-map render itself.
    LaunchedEffect(offlineRegions) {
        mapboxMap?.getStyle { style ->
            val decision = soy.engindearing.omnitak.mobile.data.offline.OfflineTilePolicy.decide(
                offlineRegions, app.offlineRegionStore.networkAvailable(),
            )
            soy.engindearing.omnitak.mobile.ui.components.KmlOverlayRenderer
                .applyOfflineRegions(style, offlineRegions, app.offlineRegionStore, decision)
        }
    }
    // Frame raster/MBTiles bounds on request.
    val mbZoomBounds by soy.engindearing.omnitak.mobile.ui.components.KmlOverlayEvents.zoomBounds.collectAsState()
    LaunchedEffect(mbZoomBounds) {
        val b = mbZoomBounds ?: return@LaunchedEffect
        if (userPrefs.cesiumGlobeEnabled) {
            app.userPrefsStore.update { it.copy(cesiumGlobeEnabled = false) }
        }
        mapboxMap?.let { m ->
            val bounds = org.maplibre.android.geometry.LatLngBounds.from(b[0], b[2], b[1], b[3])
            runCatching {
                m.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 100), 800)
            }
        }
        soy.engindearing.omnitak.mobile.ui.components.KmlOverlayEvents.boundsConsumed()
    }
    // Frame an overlay's bounds when the Map Overlays sheet requests it.
    val kmlZoom by soy.engindearing.omnitak.mobile.ui.components.KmlOverlayEvents.zoomTo.collectAsState()
    LaunchedEffect(kmlZoom) {
        val o = kmlZoom ?: return@LaunchedEffect
        if (userPrefs.cesiumGlobeEnabled) {
            app.userPrefsStore.update { it.copy(cesiumGlobeEnabled = false) }
        }
        mapboxMap?.let { m ->
            val bounds = org.maplibre.android.geometry.LatLngBounds.from(o.maxLat, o.maxLon, o.minLat, o.minLon)
            runCatching {
                m.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 100), 800)
            }
        }
        soy.engindearing.omnitak.mobile.ui.components.KmlOverlayEvents.consumed()
    }

    // Shared engine inputs — Cesium and MapLibre consume the SAME filtered
    // contact list and gesture handlers, hoisted once so a fix lands on
    // both engines together. Per-engine copies are the documented OmniTAK
    // bug class "wired into one engine but missing from the other" (VC 77's
    // dead 3D zoom buttons came from exactly this split).
    val visibleContacts: List<soy.engindearing.omnitak.mobile.data.CoTEvent> =
        if (contactsVisible) {
            if (meshNodesVisible) contacts.values.toList()
            // Hide mesh-origin contacts — they all share the `MESHTASTIC-`
            // UID prefix produced by `MeshtasticCoTConverter.takUid`.
            else contacts.values.filterNot { it.uid.startsWith("MESHTASTIC-") }
        } else {
            emptyList()
        }
    // Native contacts (#77) + drawings (#80) render through the Annotation API,
    // which only re-renders on camera-idle or a style reload — so a marker
    // created/edited/deleted (or a drawing changed) wouldn't show until you
    // panned or toggled 2D/3D. Drive a data-keyed refresh (mirrors the KML
    // renderers) so create/update/delete reflects immediately.
    // #178 — drive a slow tick so the on-map staleness overlay re-renders the
    // age labels/fade as points get older, even with no pan/zoom or CoT update.
    // Only ticks while the overlay is enabled, so the default path is unaffected.
    val stalenessOverlay = userPrefs.stalenessOverlayEnabled
    var stalenessTick by remember { mutableStateOf(0) }
    LaunchedEffect(stalenessOverlay) {
        while (stalenessOverlay) {
            kotlinx.coroutines.delay(30_000L)
            stalenessTick++
        }
    }
    LaunchedEffect(visibleContacts, mapboxMap, stalenessOverlay, stalenessTick) {
        mapboxMap?.let { m ->
            soy.engindearing.omnitak.mobile.ui.components.ContactMarkerRenderer
                .update(m, appContext, visibleContacts, stalenessOverlay)
        }
    }
    // #152 — render user drawings AND the transient range rings through the
    // single native renderer. apply() clears and redraws the whole set, so the
    // rings must ride in the SAME list; a separate call would wipe the drawings.
    LaunchedEffect(drawings, drawingsVisible, mapboxMap, rangeRingCenter) {
        mapboxMap?.let { m ->
            val base = if (drawingsVisible) drawings else emptyList()
            soy.engindearing.omnitak.mobile.ui.components.DrawingShapeRenderer
                .apply(m, base + buildRangeRingDrawings(rangeRingCenter))
        }
    }
    val handleMapLongPress: (LatLng, Offset) -> Unit = { latLng, offset ->
        if (!measurementActive && !rangeRingsActive) {
            radialLatLng = latLng
            radialAnchor = offset
        }
    }
    val handleContactTap: (soy.engindearing.omnitak.mobile.data.CoTEvent) -> Unit = { event ->
        if (!measurementActive && !rangeRingsActive) {
            editingMarker = event
            markerSheetLatLng = LatLng(event.lat, event.lon)
        }
    }
    // Compose-observable camera target — MapCameraStore's plain fields
    // don't trigger recomposition, so viewport-dependent layers (grid,
    // ADSB box) hang off this state instead. Seeded from the persisted
    // camera so cold start has a center before the first idle event.
    var cameraTarget by remember {
        mutableStateOf(
            run {
                val lat = app.mapCameraStore.lastTargetLat
                val lon = app.mapCameraStore.lastTargetLon
                if (lat != null && lon != null) LatLng(lat, lon) else null
            }
        )
    }
    val handleCameraChanged: (LatLng, Double, Double) -> Unit = { target, zoom, bearing ->
        cameraTarget = target
        currentBearing = bearing
        app.mapCameraStore.update(target.latitude, target.longitude, zoom, bearing)
    }
    // MeshTAK: ADS-B plugin hooks removed with the plugin subsystem.

    // Issue #97 — keep-screen-on now lives at the AppNav / single-Activity
    // scope (see AppNav.kt) so the screen stays awake on every destination
    // while OmniTAK is foreground, not just while the map tab is composed.
    // Scoping it here previously tore the flag down the moment the operator
    // opened another tab — the exact reported failure mode.

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // MeshTAK: the Cesium 3D globe branch is deferred to Phase 4 —
        // cesiumGlobeEnabled defaults false and has no toggle here yet, so
        // the 2D MapLibre engine renders unconditionally.
        run {
        TacticalMap(
            modifier = Modifier.fillMaxSize(),
            // Cold-start camera priority:
            //   1. Persisted view (DataStore via MapCameraStore) — operator's last pan/zoom.
            //   2. Self-fix — center on GPS position if available and no saved view.
            //   3. Neutral global fallback — world-level zoom, no developer home-town.
            // Spokane hardcode removed (P-E TAK Discord report 2026-05-27).
            initialCenter = run {
                val camLat = app.mapCameraStore.lastTargetLat
                val camLon = app.mapCameraStore.lastTargetLon
                when {
                    camLat != null && camLon != null -> LatLng(camLat, camLon)
                    selfFix != null -> LatLng(selfFix!!.lat, selfFix!!.lon)
                    else -> FALLBACK_GLOBAL_VIEW
                }
            },
            initialZoom = app.mapCameraStore.lastZoom
                ?: if (selfFix != null) 12.0 else FALLBACK_GLOBAL_ZOOM,
            // #78 — 3D→2D handoff: restore the rotation the globe (or the
            // prior 2D view this session) had. Session-memory only, so
            // cold start stays north-up exactly as before.
            initialBearing = app.mapCameraStore.lastBearing ?: 0.0,
            onCameraIdle = handleCameraChanged,
            onMapReady = { map -> mapboxMap = map },
            // GAP-101 / GAP-107 — react to the basemap selection from Settings.
            // WMTS_CUSTOM uses the operator-pasted XYZ tile URL.
            styleJson = styleJsonForProvider(userPrefs.mapProvider, userPrefs.customTileUrl, terrain3d = map3dEnabled),
            terrain3d = map3dEnabled,
            onMapLongPress = handleMapLongPress,
            onContactTap = handleContactTap,
            onMapSingleTap = onMapSingleTap@ { latLng ->
                // #152 — range-rings mode owns the tap: it just (re)sets the
                // center, so it wins before any waypoint/placement hit-test.
                if (rangeRingsActive) {
                    rangeRingCenter = latLng
                    return@onMapSingleTap true
                }
                // MeshTAK: UAS mission-waypoint hit-test/add removed.
                when {
                    measurementActive -> {
                        measurementPoints = measurementPoints + latLng
                        true
                    }
                    drawingKind != null -> {
                        drawingPoints = drawingPoints + latLng
                        true
                    }
                    // Issue #76 — a tap that didn't hit a waypoint and isn't in
                    // a placement mode hit-tests placed drawings so the operator
                    // can select one to edit / move / delete. Skip while moving.
                    movingDrawingId == null && drawingsVisible -> {
                        val map = mapboxMap
                        if (map == null) {
                            false
                        } else {
                            val tap = map.projection.toScreenLocation(latLng)
                            // Defer to markers: if a visible contact sits under
                            // the tap, let the contact handler open its sheet
                            // (markers are the more precise target and own the
                            // single-tap → edit flow).
                            val contactUnderTap = visibleContacts.any { c ->
                                val p = map.projection.toScreenLocation(LatLng(c.lat, c.lon))
                                kotlin.math.hypot(
                                    (p.x - tap.x).toDouble(),
                                    (p.y - tap.y).toDouble(),
                                ) < CONTACT_TAP_DEFER_PX
                            }
                            val hit = if (contactUnderTap) {
                                null
                            } else {
                                val projected = drawings.map { d ->
                                    soy.engindearing.omnitak.mobile.domain.DrawingHitTest.Projected(
                                        id = d.id,
                                        kind = d.kind,
                                        points = d.points.map { (lat, lon) ->
                                            val p = map.projection.toScreenLocation(LatLng(lat, lon))
                                            p.x to p.y
                                        },
                                    )
                                }
                                soy.engindearing.omnitak.mobile.domain.DrawingHitTest.hitId(
                                    drawings = projected,
                                    px = tap.x,
                                    py = tap.y,
                                    tolerancePx = DRAWING_TAP_TOLERANCE_PX,
                                )
                            }
                            if (hit != null) {
                                selectedDrawingId = hit
                                true
                            } else {
                                false
                            }
                        }
                    }
                    else -> false
                }
            },
            locationEnabled = locationGranted,
            recenterTrigger = recenterTick,
            zoomInTrigger = zoomInTick,
            zoomOutTrigger = zoomOutTick,
            contacts = visibleContacts,
            measurementPoints = measurementPoints,
            // #152 — range rings ride the drawing list so they survive style
            // reloads (TacticalMap re-applies this param after every reload).
            drawings = (if (drawingsVisible) {
                drawings + buildInProgressDrawing(drawingKind, drawingPoints)
            } else {
                emptyList()
            }) + buildRangeRingDrawings(rangeRingCenter),
            // Graticule follows the viewport (was hardcoded to Mountain
            // View — invisible for any user outside ±2° of the
            // Googleplex). Quantized to 0.5° so micro-pans don't re-push
            // the GeoJSON; the ±2° box still covers the view with ≤0.25°
            // of drift before the next camera-idle refresh.
            gridCenter = if (gridEnabled) {
                val c = cameraTarget
                    ?: selfFix?.let { LatLng(it.lat, it.lon) }
                    ?: FALLBACK_GLOBAL_VIEW
                LatLng(
                    kotlin.math.round(c.latitude * 2.0) / 2.0,
                    kotlin.math.round(c.longitude * 2.0) / 2.0,
                )
            } else null,
            panTarget = panTarget,
            panTargetTick = panTargetTick,
            followMeActive = followMeActive,
            useMilStdSelfSymbol = userPrefs.useMilStdSelfSymbol,
            selfTeamColor = userPrefs.team,
            selfMarkerTriangle = userPrefs.selfMarkerTriangle,
            // Issue #75 — feeds the puck so a restored fix renders
            // immediately on cold start (dimmed when stale) and the
            // forced foreground-resume fix lands without waiting on the
            // LocationComponent's internal engine.
            selfFix = effectiveSelfFix,
            onSelfMarkerTap = { selfMarkerEditOpen = true },
            onStyleReady = { _, style ->
                soy.engindearing.omnitak.mobile.ui.components.KmlOverlayRenderer
                    .apply(style, kmlOverlays, app.kmlOverlayStore)
                soy.engindearing.omnitak.mobile.ui.components.KmlOverlayRenderer
                    .applyMBTiles(style, mbtilesOverlays, app.mbtilesOverlayStore)
                soy.engindearing.omnitak.mobile.ui.components.KmlOverlayRenderer
                    .applyRaster(style, rasterImagery, app.rasterOverlayStore)
            },
            // Issue #95 — north-up lock + tap-to-reset support.
            northUpLocked = userPrefs.northUpLocked,
            snapNorthTrigger = snapNorthTick,
            // Issue #89 — feed the status-bar inset so the built-in MapLibre
            // compass margin clears the inset-shifted ATAKStatusBar.
            topInsetDp = statusBarTopInsetDp.value,
        )
        // MeshTAK: plugin map-overlay hooks removed with the plugin subsystem.
        }

        // Issue #16 — lasso freehand multi-select overlay. Renders
        // ABOVE the map so the dashed orange path stays on top of
        // marker layers (the Android equivalent of the iOS
        // CAShapeLayer fix). Inert when `active = false` — pan/zoom
        // continue to land on TacticalMap.
        // Drawing → LassoDrawing adapter. Drawing.id is a String (TAK
        // UID style); LassoDrawing.id is UUID for parity with the iOS
        // service shape. We project the string onto a deterministic
        // UUID via nameUUIDFromBytes so the same drawing always maps
        // to the same UUID across recompositions and we can reverse
        // the mapping during bulk delete.
        val lassoDrawingIdMap = remember(drawings) {
            drawings.associate {
                java.util.UUID.nameUUIDFromBytes(it.id.toByteArray()) to it.id
            }
        }
        val lassoDrawings = remember(drawings) {
            drawings.map { d ->
                soy.engindearing.omnitak.mobile.domain.LassoDrawing(
                    id = java.util.UUID.nameUUIDFromBytes(d.id.toByteArray()),
                    coordinates = d.points.map { (lat, lon) ->
                        soy.engindearing.omnitak.mobile.domain.LassoLatLng(latitude = lat, longitude = lon)
                    },
                )
            }
        }


        soy.engindearing.omnitak.mobile.ui.components.LassoOverlay(
            active = lassoMode,
            mapboxMap = mapboxMap,
            markers = (contacts.values).map { c ->
                soy.engindearing.omnitak.mobile.domain.LassoMarker(
                    id = c.uid,
                    coordinate = soy.engindearing.omnitak.mobile.domain.LassoLatLng(
                        latitude = c.lat,
                        longitude = c.lon,
                    ),
                )
            },
            drawings = lassoDrawings,
            onCompleted = { lassoMode = false },
            onCancelled = { lassoMode = false },
        )

        // Issue #76 — drag-to-reposition the selected drawing. Rendered above
        // the map (like the lasso) so it owns the drag while active; inert
        // (returns immediately) when no drawing is being moved.
        soy.engindearing.omnitak.mobile.ui.components.DrawingMoveOverlay(
            drawing = movingDrawingId?.let { id -> drawings.firstOrNull { it.id == id } },
            mapboxMap = mapboxMap,
            onMoved = { moved -> app.drawingStore.update(moved) },
            onDone = { movingDrawingId = null },
        )

        // Issue #16 — selection pill. Surfaces "N selected" whenever
        // there's an active lasso selection. Tap → action sheet.
        if (lassoSelection.totalCount > 0) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 90.dp, end = 16.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                soy.engindearing.omnitak.mobile.ui.components.LassoSelectionPill(
                    count = lassoSelection.totalCount,
                    onShowActions = { lassoActionsOpen = true },
                )
            }
        }
        if (lassoActionsOpen) {
            // Resolve the selection's UIDs back to the live CoTEvent
            // objects in the contact store. Filters out anything
            // that's already been removed since the lasso closed.
            val selectedEvents = lassoSelection.markerIDs.mapNotNull { contacts[it] }

            soy.engindearing.omnitak.mobile.ui.components.LassoActionsSheet(
                selectionCount = lassoSelection.totalCount,
                onDismiss = { lassoActionsOpen = false },
                onAddToDataPackage = {
                    lassoActionsOpen = false
                    if (selectedEvents.isEmpty()) {
                        toast("Selection is empty")
                    } else {
                        val ctx = appContext
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                soy.engindearing.omnitak.mobile.domain.LassoExporters.writeMissionPackage(
                                    context = ctx,
                                    name = "Lasso selection (${selectedEvents.size})",
                                    events = selectedEvents,
                                )
                            }
                            val shared = soy.engindearing.omnitak.mobile.domain.LassoExporters.shareFile(
                                context = ctx,
                                file = file,
                                mimeType = "application/zip",
                                title = "Lasso data package",
                            )
                            toast(
                                if (shared) "Mission package built · share sheet open"
                                else "Saved package to ${file.parentFile?.name}/${file.name}"
                            )
                        }
                    }
                },
                onUploadToServer = run {
                    val anyEnabledTls = app.serverManager.servers.collectAsState().value
                        .any { it.enabled && it.useTLS && it.certificateName != null }
                    if (!anyEnabledTls || selectedEvents.isEmpty()) {
                        null
                    } else {
                        {
                            lassoActionsOpen = false
                            val ctx = appContext
                            val ts = System.currentTimeMillis()
                            val pkgName = "lasso-${selectedEvents.size}-$ts"
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    soy.engindearing.omnitak.mobile.domain.LassoExporters
                                        .writeMissionPackage(
                                            context = ctx,
                                            name = pkgName,
                                            events = selectedEvents,
                                        )
                                }
                                // MeshTAK: mission-sync server upload not ported —
                                // the package is written locally and surfaced.
                                toast("Data package written: ${file.name}")
                            }
                        }
                    }
                },
                onExportKML = {
                    lassoActionsOpen = false
                    if (selectedEvents.isEmpty()) {
                        toast("Selection is empty")
                    } else {
                        val ctx = appContext
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                soy.engindearing.omnitak.mobile.domain.LassoExporters.writeKml(
                                    context = ctx,
                                    name = "Lasso selection (${selectedEvents.size})",
                                    events = selectedEvents,
                                )
                            }
                            val shared = soy.engindearing.omnitak.mobile.domain.LassoExporters.shareFile(
                                context = ctx,
                                file = file,
                                mimeType = "application/vnd.google-earth.kml+xml",
                                title = "Lasso KML",
                            )
                            toast(
                                if (shared) "KML built · share sheet open"
                                else "Saved KML to ${file.parentFile?.name}/${file.name}"
                            )
                        }
                    }
                },
                onSendToContacts = {
                    lassoActionsOpen = false
                    if (selectedEvents.isEmpty()) {
                        toast("Selection is empty")
                    } else {
                        contactPickerOpen = true
                    }
                },
                onBulkDelete = {
                    val n = lassoSelection.totalCount
                    // Wire identity is the persisted EUD UID (#9) — the
                    // callsign is a display label and never matches real
                    // ContactStore uids, so guarding on it was a no-op.
                    val mySelfUid = userPrefs.selfUid.takeIf { it.isNotBlank() }
                    val targetEvents = selectedEvents
                    // Self-marker guard: never accidentally delete our
                    // own CoT — that would propagate "delete me" to
                    // every peer.
                    val protected = setOfNotNull(mySelfUid)
                    val toRemove = targetEvents.filterNot { it.uid in protected }

                    // 1) Local removal — ContactStore is the source of
                    //    truth for the map renderer; drop them
                    //    immediately so the operator sees the result.
                    toRemove.forEach { app.contactStore.remove(it.uid) }
                    // 2) Drawings: project selection UUIDs back to the
                    //    drawing.id String via the side-map built when
                    //    we adapted Drawings → LassoDrawings.
                    val deletedDrawings = lassoSelection.drawingIDs
                        .mapNotNull { lassoDrawingIdMap[it] }
                    deletedDrawings.forEach { app.drawingStore.remove(it) }
                    // 3) Broadcast — for each deleted marker, fire a
                    //    t-x-d-d tombstone on the active server so
                    //    other EUDs propagate the removal. Best-effort:
                    //    if the connection is down the local removal
                    //    still stands.
                    scope.launch {
                        // Tombstones go out under the same persisted EUD
                        // UID as PPLI/chat/markers — not the callsign.
                        val senderUid = app.userPrefsStore.ensureSelfUid()
                        toRemove.forEach { e ->
                            val xml = soy.engindearing.omnitak.mobile.domain.CotBuilders
                                .buildDeleteEvent(targetUid = e.uid, senderUid = senderUid)
                            runCatching { app.serverManager.sendCoT(xml) }
                        }
                    }
                    lassoService.clear()
                    lassoActionsOpen = false
                    val drawingNote = if (deletedDrawings.isNotEmpty())
                        " + ${deletedDrawings.size} drawing(s)" else ""
                    toast(
                        if (toRemove.size == targetEvents.size)
                            "Deleted ${toRemove.size} marker(s)$drawingNote + broadcast"
                        else
                            "Deleted ${toRemove.size}/$n (self skipped)$drawingNote"
                    )
                },
                onClear = {
                    lassoService.clear()
                    lassoActionsOpen = false
                },
            )
        }
        if (contactPickerOpen) {
            // Snapshot the events the lasso captured AND the rest of
            // ContactStore so the picker has someone to send TO. We
            // exclude the selection itself — sending markers to
            // themselves makes no sense.
            val selected = lassoSelection.markerIDs
            soy.engindearing.omnitak.mobile.ui.components.ContactPickerDialog(
                title = "Send ${lassoSelection.totalCount} item(s) to…",
                candidates = contacts.values.toList(),
                excludeUids = selected,
                onDismiss = { contactPickerOpen = false },
                onConfirm = { destUids ->
                    contactPickerOpen = false
                    if (destUids.isEmpty()) {
                        toast("No recipients selected")
                        return@ContactPickerDialog
                    }
                    val selectedEvents = selected.mapNotNull { contacts[it] }
                    scope.launch {
                        var sent = 0
                        selectedEvents.forEach { e ->
                            val xml = soy.engindearing.omnitak.mobile.domain.CotBuilders
                                .rebuildEvent(e, destUids.toList())
                            if (runCatching { app.serverManager.sendCoT(xml) }
                                    .getOrDefault(false)
                            ) sent++
                        }
                        toast("Sent $sent/${selectedEvents.size} → ${destUids.size} recipient(s)")
                    }
                },
            )
        }

        // ATAKStatusBar removed (closed-test feedback: it covered the map and
        // duplicated affordances — Settings lives on the bottom toolbar,
        // Servers in the toolbar catalog, GPS accuracy on the self-position
        // card). Dropping it also killed the 15 s clock ticker that
        // recomposed this whole screen while idle.

        // PPLI self-position card — bottom-right, mirrors iOS layout.
        // GAP-030b — coordinates pull from FusedLocationProviderClient
        // (LocationProvider). Until a fix arrives we show "Acquiring
        // fix…" so users in Germany don't see San Francisco (issue #10).
        if (callsignCardVisible) {
            val fix = effectiveSelfFix
            SelfPositionCard(
                callsign = userPrefs.callsign,
                coordinateLabel = if (fix != null) {
                    soy.engindearing.omnitak.mobile.data.CoordFormatter.position(
                        fix.lat, fix.lon, userPrefs.coordFormat,
                    )
                } else {
                    "Acquiring fix…"
                },
                // Show "—" (not formatted zeros) until a real fix lands, so the
                // card can't read "Acquiring fix…" next to a perfect 0 m / 0
                // km/h / ±0 m telemetry block.
                altitudeLabel = fix?.altitudeM?.let {
                    soy.engindearing.omnitak.mobile.data.CoordFormatter.altitude(it, userPrefs.distanceUnit)
                } ?: "—",
                speedLabel = fix?.speedKmh?.let {
                    soy.engindearing.omnitak.mobile.data.CoordFormatter.speed(it, userPrefs.distanceUnit)
                } ?: "—",
                accuracyLabel = fix?.accuracyM?.let {
                    soy.engindearing.omnitak.mobile.data.CoordFormatter.accuracy(it.toInt(), userPrefs.distanceUnit)
                } ?: "—",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 96.dp),
            )
        }

        if (manualSelfFix != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .clickable { app.locationProvider.setManualFix(null) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = soy.engindearing.omnitak.mobile.ui.theme.TacticalAccent.copy(alpha = 0.15f),
                    tonalElevation = 2.dp,
                ) {
                    androidx.compose.material3.Text(
                        "Manual position · tap to resume GPS",
                        color = soy.engindearing.omnitak.mobile.ui.theme.TacticalAccent,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        ToolsDrawer(
            tools = listOf(
                ToolEntry("draw", Icons.Filled.Brush, "Drawing"),
                ToolEntry("measure", Icons.Filled.Straighten, "Measure"),
                ToolEntry("rangerings", Icons.Filled.TrackChanges, "Range Rings"),
                ToolEntry("reports", Icons.Filled.Description, "Reports"),
                ToolEntry("layers", Icons.Filled.Layers, "Layers"),
                // ADS-B moved to the ADS-B plugin — its on/off control now
                // lives in Settings → Plugins → ADS-B (the plugin's
                // settingsContent), not the Tools drawer.
                ToolEntry("chat", Icons.Filled.Chat, "Chat"),
                ToolEntry("missionsync", Icons.Filled.Sync, "Mission Sync"),
                ToolEntry("fema", Icons.Filled.LocalFireDepartment, "FEMA / IC"),
                ToolEntry("teams", Icons.Filled.Groups, "Teams"),
                ToolEntry(
                    "nav",
                    Icons.Filled.Navigation,
                    if (followMeActive) "Follow on" else "Follow",
                ),
            ),
            onSelect = { tool ->
                when (tool.id) {
                    "measure" -> {
                        rangeRingsActive = false
                        measurementActive = true
                        measurementPoints = emptyList()
                        toast("Measure mode — tap map to add points")
                    }
                    "rangerings" -> {
                        measurementActive = false
                        drawingKind = null
                        rangeRingsActive = true
                        rangeRingCenter = null
                        toast("Range rings — tap map to set center")
                    }
                    "reports" -> reportsSheetOpen = true
                    "draw" -> {
                        rangeRingsActive = false
                        drawingPickerOpen = true
                    }
                    "layers" -> layersSheetOpen = true
                    "chat" -> onOpenTab("chat")
                    "missionsync" -> onOpenTab("missionsync")
                    "fema" -> {
                        val lat = app.mapCameraStore.lastTargetLat
                        val lon = app.mapCameraStore.lastTargetLon
                        femaPaletteLatLng = if (lat != null && lon != null) LatLng(lat, lon)
                        else selfFix?.let { LatLng(it.lat, it.lon) } ?: FALLBACK_GLOBAL_VIEW
                    }
                    "teams" -> teamsPanelOpen = true
                    "nav" -> {
                        if (!locationGranted) {
                            toast("Follow-me needs location permission")
                        } else {
                            val next = !followMeActive
                            mutatePref { it.copy(followMeActive = next) }
                            toast(if (next) "Follow me ON" else "Follow me OFF")
                        }
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd),
        )

        // Issue #96 — compass overlay. Floats at top-start corner, just below
        // the status bar. Shows live bearing; tapping snaps map to north.
        // The MapLibre built-in compass is already on top-start (issue #81),
        // but it disappears when north-up — this one is always visible and
        // provides the manual snap action.
        CompassOverlay(
            bearingDeg = currentBearing,
            onTapToNorth = {
                if (userPrefs.northUpLocked) {
                    // Already locked — snap is automatic; just a no-op tap.
                } else {
                    snapNorthTick++
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                // Issue #89 — under enableEdgeToEdge the map draws beneath the
                // system status bar, so the compass still needs the inset to
                // clear the clock/notch (the ATAKStatusBar it used to sit
                // under is gone).
                .padding(start = 12.dp, top = 16.dp + statusBarTopInsetDp),
        )

        // Map control stack — zoom in / zoom out / center-on-me — at the
        // BottomStart corner so it stays reachable one-handed without
        // opening the tools drawer. Mirrors the iOS map controls layout.
        // #182 — in landscape the short height can't spare a tall column, so
        // the same buttons lay out in a horizontal row hugging the bottom edge.
        val mapControlButtons: @Composable () -> Unit = {
            MapControlFab(
                icon = Icons.Filled.Add,
                contentDescription = "Zoom in",
                tint = TacticalAccent,
                onClick = { zoomInTick++ },
            )
            MapControlFab(
                icon = Icons.Filled.Remove,
                contentDescription = "Zoom out",
                tint = TacticalAccent,
                onClick = { zoomOutTick++ },
            )
            MapControlFab(
                icon = Icons.Filled.MyLocation,
                contentDescription = "Center on me",
                tint = if (locationGranted) TacticalAccent else androidx.compose.ui.graphics.Color.Gray,
                enabled = locationGranted,
                onClick = { recenterTick++ },
            )
            // MeshTAK: center-on-drone FAB removed with the UAS subsystem.
        }
        if (isLandscape) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // Lower bottom inset than portrait: the bottom toolbar is a
                    // single compact row in landscape, so the controls sit just
                    // above the navigation-bar inset instead of clearing a tall bar.
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                mapControlButtons()
            }
        } else {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 110.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                mapControlButtons()
            }
        }

        // remember(): MapScreen recomposes constantly while GPS streams;
        // rebuilding this list (and re-laying-out the whole ring) on every
        // pass was the radial menu's main jank source. Static in MeshTAK —
        // UAS and plugin actions are not ported.
        val radialActions = remember {
            buildList {
                add(RadialAction("drop", Icons.Filled.Place, "Drop Marker"))
                add(RadialAction("measure", Icons.Filled.Straighten, "Measure"))
                // "Navigate" removed — Android has no route-planning /
                // turn-by-turn engine yet (iOS executeNavigate rides
                // routePlanningService). Re-add when that engine lands;
                // an action that does nothing is worse than no action.
                add(RadialAction("layers", Icons.Filled.Layers, "Layers"))
                add(RadialAction("copy", Icons.Filled.LocationOn, "Copy Coords"))
                add(RadialAction("center", Icons.Filled.Explore, "Center"))
                add(RadialAction("add", Icons.Filled.Add, "Add"))
            }
        }
        RadialMenu(
            visible = radialAnchor != null,
            anchor = radialAnchor ?: Offset.Zero,
            actions = radialActions,
            onSelect = { action ->
                val ll = radialLatLng
                radialAnchor = null
                radialLatLng = null
                when (action.id) {
                    "drop" -> if (ll != null) markerSheetLatLng = ll
                    // "Add" = quick-add at the long-press point — same
                    // marker-creation sheet as Drop (GAP-052 parity with
                    // iOS RadialMenuActionExecutor).
                    "add" -> if (ll != null) markerSheetLatLng = ll
                    "layers" -> layersSheetOpen = true
                    "measure" -> {
                        // Enter measure mode seeded with the long-press
                        // point — matches iOS executeMeasure.
                        measurementActive = true
                        measurementPoints = ll?.let { listOf(it) } ?: emptyList()
                        toast(Loc.t("map.toast.measure"))
                    }
                    "copy" -> if (ll != null) {
                        val coord = soy.engindearing.omnitak.mobile.data.CoordFormatter
                            .position(ll.latitude, ll.longitude, userPrefs.coordFormat)
                        // Issue #92 — verified platform write; the toast
                        // only claims "Copied" when the clip read back
                        // intact, so an OEM-side silent drop reports
                        // failure instead of lying to the operator.
                        if (soy.engindearing.omnitak.mobile.data.CoordClipboard.copy(appContext, coord)) {
                            toast(Loc.t("map.toast.copied", coord))
                        } else {
                            toast(Loc.t("map.toast.copyFailed"))
                        }
                    }
                    "center" -> if (ll != null) {
                        panTarget = ll
                        panTargetTick += 1
                        if (followMeActive) mutatePref { it.copy(followMeActive = false) }
                        toast(
                            Loc.t(
                                "map.toast.panning",
                                soy.engindearing.omnitak.mobile.data.CoordFormatter
                                    .position(ll.latitude, ll.longitude, userPrefs.coordFormat),
                            )
                        )
                    }
                }
            },
            onDismiss = {
                radialAnchor = null
                radialLatLng = null
            },
        )

        MarkerEditSheet(
            visible = markerSheetLatLng != null,
            latLng = markerSheetLatLng,
            initialCallsign = editingMarker?.callsign ?: "Marker ${contacts.size + 1}",
            initialAffiliation = editingMarker?.affiliation ?: CoTAffiliation.FRIEND,
            initialAltitude = editingMarker?.hae?.takeIf { it != 0.0 },
            initialRemarks = editingMarker?.remarks ?: "",
            // Issue #98 — re-open an existing marker on the symbol it carries.
            initialCotType = editingMarker?.type,
            initialIconsetPath = editingMarker?.iconsetPath,
            initialCourseHeading = editingMarker?.courseHeading,
            // #178 / #180 — pass the live contact so the sheet can show point
            // age + data source. Null when dropping a brand-new marker.
            contact = editingMarker,
            editing = editingMarker != null,
            onSave = { result ->
                val ll = markerSheetLatLng
                if (ll != null) {
                    val uid = editingMarker?.uid ?: "local-${System.currentTimeMillis()}"
                    val event = CoTEvent(
                        uid = uid,
                        // Issue #98 — use the picked MIL-STD symbol when the
                        // operator chose one; otherwise keep the generic
                        // per-affiliation point so the pre-icon-suite default
                        // is preserved.
                        type = result.cotType ?: "a-${result.affiliation.code}-G-U-C",
                        lat = ll.latitude,
                        lon = ll.longitude,
                        hae = result.altitudeMeters ?: 0.0,
                        callsign = result.callsign,
                        remarks = result.remarks,
                        // Issue #98 — Spot Map (or other TAK-suite) glyph. The
                        // iconset path + colour ride the wire so peers render
                        // the identical dot; null for MIL-STD picks.
                        iconsetPath = result.iconsetPath,
                        colorArgb = result.argbHex?.toLong(16)?.toInt(),
                        courseHeading = result.courseHeading ?: editingMarker?.courseHeading,
                        // #180 — operator-created marker; tag the transport as
                        // local so the detail sheet doesn't mislabel its origin.
                        source = soy.engindearing.omnitak.mobile.data.CoTSource.LOCAL,
                    )
                    app.contactStore.ingest(event)
                    val verb = if (editingMarker != null) Loc.t("marker.verb.updated")
                    else Loc.t("marker.verb.saved")
                    // Broadcast the marker to every connected server so
                    // teammates actually see it — same path the FEMA
                    // palette uses. The local ingest above stands on its
                    // own for offline work; the toast tells the operator
                    // which of the two actually happened.
                    scope.launch {
                        val xml = soy.engindearing.omnitak.mobile.domain.CotBuilders
                            .rebuildEvent(event, destUids = emptyList())
                        val sent = runCatching { app.serverManager.sendCoT(xml) }
                            .getOrDefault(false)
                        // #171 — also fan the marker out over the active mesh
                        // (TAKPacketV2 on port 78) so off-grid peers see it.
                        runCatching { app.activeMeshManager.sendCoTOverMesh(event) }
                        toast(
                            if (sent) Loc.t("marker.toast.sent", verb, result.callsign)
                            else Loc.t("marker.toast.local", verb, result.callsign)
                        )
                    }
                }
                markerSheetLatLng = null
                editingMarker = null
            },
            onDelete = editingMarker?.let {
                {
                    app.contactStore.remove(it.uid)
                    toast("Deleted marker “${it.callsign ?: it.uid}”")
                    markerSheetLatLng = null
                    editingMarker = null
                }
            },
            // MeshTAK: UAS pursue action not ported.
            onPursueWithUas = null,
            onDismiss = {
                markerSheetLatLng = null
                editingMarker = null
            },
        )

        if (selfMarkerEditOpen) {
            MarkerEditSheet(
                visible = true,
                latLng = effectiveSelfFix?.let { org.maplibre.android.geometry.LatLng(it.lat, it.lon) },
                initialCallsign = userPrefs.callsign,
                initialAffiliation = soy.engindearing.omnitak.mobile.data.CoTAffiliation.FRIEND,
                editing = true,
                editingSelf = true,
                initialSelfLat = effectiveSelfFix?.lat,
                initialSelfLon = effectiveSelfFix?.lon,
                onSave = { result ->
                    prefScope.launch { app.userPrefsStore.update { it.copy(callsign = result.callsign) } }
                    val newLat = result.selfLatOverride
                    val newLon = result.selfLonOverride
                    if (newLat != null && newLon != null) {
                        // #82 — push the manual position into LocationProvider so
                        // the puck, the card, and the PPLI broadcast all use it.
                        // accuracyM = NaN marks it as a manual drop (unknown CE on
                        // the wire); altitude inherits the last GPS fix's HAE.
                        app.locationProvider.setManualFix(
                            soy.engindearing.omnitak.mobile.data.SelfFix(
                                lat = newLat,
                                lon = newLon,
                                altitudeM = selfFix?.altitudeM ?: 0.0,
                                speedKmh = 0.0,
                                accuracyM = Float.NaN,
                                timeMs = System.currentTimeMillis(),
                            )
                        )
                    }
                    selfMarkerEditOpen = false
                },
                onDismiss = { selfMarkerEditOpen = false },
            )
        }

        // "Go to Coordinate" — TWD97 (7+7 / 5+5) / MGRS / Lat-Lon entry.
        if (coordEntryOpen) {
            soy.engindearing.omnitak.mobile.ui.components.CoordinateEntrySheet(
                // Reference for 5+5 TWD97: last camera centre, else self-fix.
                refLat = app.mapCameraStore.lastTargetLat ?: selfFix?.lat,
                refLon = app.mapCameraStore.lastTargetLon ?: selfFix?.lon,
                onGo = { lat, lon, drop ->
                    coordEntryOpen = false
                    panTarget = LatLng(lat, lon)
                    panTargetTick += 1
                    if (followMeActive) mutatePref { it.copy(followMeActive = false) }
                    // Honor the operator's coordinate-format pref (#3/#4),
                    // matching the radial "Add @ …" toast above.
                    val coordText = soy.engindearing.omnitak.mobile.data.CoordFormatter
                        .position(lat, lon, userPrefs.coordFormat)
                    if (drop) {
                        val uid = "local-${System.currentTimeMillis()}"
                        val event = CoTEvent(
                            uid = uid,
                            type = "a-f-G-U-C",
                            lat = lat,
                            lon = lon,
                            hae = 0.0,
                            callsign = "Marker ${contacts.size + 1}",
                            remarks = "",
                            source = soy.engindearing.omnitak.mobile.data.CoTSource.LOCAL,
                        )
                        app.contactStore.ingest(event)
                        // Same broadcast contract as MarkerEditSheet —
                        // peers see the dropped marker, offline stays local.
                        scope.launch {
                            val xml = soy.engindearing.omnitak.mobile.domain.CotBuilders
                                .rebuildEvent(event, destUids = emptyList())
                            val sent = runCatching { app.serverManager.sendCoT(xml) }
                                .getOrDefault(false)
                            // #171 — fan out over the active mesh too.
                            runCatching { app.activeMeshManager.sendCoTOverMesh(event) }
                            toast(
                                if (sent) Loc.t("marker.toast.droppedSent", coordText)
                                else Loc.t("marker.toast.droppedLocal", coordText)
                            )
                        }
                    } else {
                        toast(Loc.t("map.toast.panning", coordText))
                    }
                },
                onDismiss = { coordEntryOpen = false },
            )
        }

        soy.engindearing.omnitak.mobile.ui.components.FemaMarkerPaletteSheet(
            visible = femaPaletteLatLng != null,
            latLng = femaPaletteLatLng,
            onConfirm = { picked ->
                val ll = femaPaletteLatLng
                if (ll != null) {
                    val uid = "local-fema-${System.currentTimeMillis()}"
                    app.contactStore.ingest(
                        soy.engindearing.omnitak.mobile.data.CoTEvent(
                            uid = uid,
                            type = picked.icon.cotType,
                            lat = ll.latitude,
                            lon = ll.longitude,
                            hae = 0.0,
                            callsign = picked.name,
                            remarks = picked.remarks,
                            iconsetPath = picked.icon.iconsetPath,
                            source = soy.engindearing.omnitak.mobile.data.CoTSource.LOCAL,
                        )
                    )
                    // Broadcast to every enabled server so peers with the
                    // FEMA catalog see the right glyph; receivers without
                    // it fall back to the friendly-installation render
                    // ATAK/iTAK does for `a-f-G-I-*`.
                    val femaEvent = soy.engindearing.omnitak.mobile.data.CoTEvent(
                        uid = uid,
                        type = picked.icon.cotType,
                        lat = ll.latitude,
                        lon = ll.longitude,
                        callsign = picked.name,
                        remarks = picked.remarks,
                        iconsetPath = picked.icon.iconsetPath,
                    )
                    scope.launch {
                        val xml = soy.engindearing.omnitak.mobile.domain.CotBuilders
                            .rebuildEvent(femaEvent, destUids = emptyList())
                        runCatching { app.serverManager.sendCoT(xml) }
                        // #171 — fan the FEMA marker out over the active mesh
                        // (TAKPacketV2 carries the iconset so peers render it).
                        runCatching { app.activeMeshManager.sendCoTOverMesh(femaEvent) }
                    }
                    toast("Dropped ${picked.icon.label} “${picked.name}”")
                }
                femaPaletteLatLng = null
            },
            onDismiss = { femaPaletteLatLng = null },
        )

        if (drawingKind != null) {
            DrawingOverlay(
                kind = drawingKind!!,
                pointCount = drawingPoints.size,
                onUndo = {
                    if (drawingPoints.isNotEmpty()) {
                        drawingPoints = drawingPoints.dropLast(1)
                    }
                },
                onCancel = {
                    drawingKind = null
                    drawingPoints = emptyList()
                },
                onFinish = {
                    val minPts = when (drawingKind!!) {
                        DrawingKind.LINE -> 2
                        DrawingKind.POLYGON -> 3
                        DrawingKind.CIRCLE -> 2
                    }
                    if (drawingPoints.size >= minPts) {
                        app.drawingStore.add(
                            Drawing(
                                id = "draw-${System.currentTimeMillis()}",
                                kind = drawingKind!!,
                                points = drawingPoints.map { it.latitude to it.longitude },
                            )
                        )
                        toast("Saved ${drawingKind!!.name.lowercase()}")
                    } else {
                        toast("Need at least $minPts points")
                    }
                    drawingKind = null
                    drawingPoints = emptyList()
                },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        if (drawingPickerOpen) {
            DrawingKindPicker(
                onPick = { kind ->
                    drawingPickerOpen = false
                    drawingKind = kind
                    drawingPoints = emptyList()
                    toast("Drawing ${kind.name.lowercase()} — tap to add points")
                },
                onDismiss = { drawingPickerOpen = false },
            )
        }

        // Issue #76 — edit / move / delete sheet for a tapped drawing. Resolves
        // the live drawing each recomposition so an external change (lasso
        // delete) closes the sheet instead of editing a ghost.
        val selectedDrawing = selectedDrawingId?.let { id -> drawings.firstOrNull { it.id == id } }
        if (selectedDrawing != null) {
            soy.engindearing.omnitak.mobile.ui.components.DrawingEditSheet(
                drawing = selectedDrawing,
                onApply = { updated -> app.drawingStore.update(updated) },
                onMove = {
                    movingDrawingId = selectedDrawing.id
                    toast("Drag the ${selectedDrawing.kind.name.lowercase()} to move it")
                },
                onDelete = {
                    app.drawingStore.remove(selectedDrawing.id)
                    toast("Deleted ${selectedDrawing.kind.name.lowercase()}")
                },
                onDismiss = { selectedDrawingId = null },
            )
        }

        if (measurementActive) {
            MeasurementOverlay(
                points = measurementPoints,
                onUndo = {
                    if (measurementPoints.isNotEmpty()) {
                        measurementPoints = measurementPoints.dropLast(1)
                    }
                },
                onClose = {
                    measurementActive = false
                    measurementPoints = emptyList()
                },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        if (rangeRingsActive) {
            RangeRingsOverlay(
                center = rangeRingCenter,
                distancesMeters = soy.engindearing.omnitak.mobile.data.rangebearing.RangeRings.DEFAULT_DISTANCES_M,
                onClear = { rangeRingCenter = null },
                onClose = {
                    rangeRingsActive = false
                    rangeRingCenter = null
                },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        if (layersSheetOpen) {
            LayersDialog(
                gridEnabled = gridEnabled,
                drawingsVisible = drawingsVisible,
                aircraftVisible = aircraftVisible,
                contactsVisible = contactsVisible,
                callsignCardVisible = callsignCardVisible,
                meshNodesVisible = meshNodesVisible,
                map3dEnabled = map3dEnabled,
                onToggleGrid = { v -> mutatePref { it.copy(gridEnabled = v) } },
                onToggleDrawings = { v -> mutatePref { it.copy(drawingsVisible = v) } },
                onToggleAircraft = { v -> mutatePref { it.copy(aircraftVisible = v) } },
                onToggleContacts = { v -> mutatePref { it.copy(contactsVisible = v) } },
                onToggleCallsignCard = { v -> mutatePref { it.copy(callsignCardVisible = v) } },
                onToggleMeshNodes = { v ->
                    scope.launch { app.userPrefsStore.setMeshNodesLayerVisible(v) }
                },
                onToggle3d = { v -> mutatePref { it.copy(map3dEnabled = v) } },
                onOpenOfflineMaps = {
                    layersSheetOpen = false
                    offlineMapsOpen = true
                },
                onDismiss = { layersSheetOpen = false },
            )
        }

        // #120 — offline map download. The default region is the current
        // viewport (read from the live map projection); null until the map
        // is ready, which the sheet handles gracefully.
        if (offlineMapsOpen) {
            val viewportBbox = remember(offlineMapsOpen) {
                mapboxMap?.projection?.visibleRegion?.latLngBounds?.let { b ->
                    soy.engindearing.omnitak.mobile.data.offline.BoundingBox(
                        north = b.getLatNorth(), south = b.getLatSouth(),
                        east = b.getLonEast(), west = b.getLonWest(),
                    )
                }
            }
            soy.engindearing.omnitak.mobile.ui.components.OfflineMapsSheet(
                initialBbox = viewportBbox,
                provider = userPrefs.mapProvider,
                customTileUrl = userPrefs.customTileUrl,
                onDismiss = { offlineMapsOpen = false },
            )
        }

        // #154 — military report entry. The report drops as a CoT point at the
        // operator's position (the location grid prefills from it, editable),
        // ingests locally so it shows on the map, and broadcasts over the active
        // TAK connection via MilitaryReportCoT.buildReportEvent — the same
        // local-ingest + sendCoT path the marker drop uses.
        if (reportsSheetOpen) {
            val rptPos = effectiveSelfFix?.let { LatLng(it.lat, it.lon) }
                ?: cameraTarget ?: selfFix?.let { LatLng(it.lat, it.lon) }
            val defaultGrid = rptPos?.let {
                soy.engindearing.omnitak.mobile.data.CoordFormatter
                    .position(it.latitude, it.longitude, userPrefs.coordFormat)
            } ?: ""
            soy.engindearing.omnitak.mobile.ui.components.MilitaryReportSheet(
                defaultLocationGrid = defaultGrid,
                onSend = { result ->
                    val pos = rptPos ?: FALLBACK_GLOBAL_VIEW
                    val uid = "report-${result.type.name.lowercase()}-${java.util.UUID.randomUUID()}"
                    val event = soy.engindearing.omnitak.mobile.data.CoTEvent(
                        uid = uid,
                        type = result.type.cot,
                        lat = pos.latitude,
                        lon = pos.longitude,
                        callsign = result.type.name,
                        remarks = result.reportText,
                        source = soy.engindearing.omnitak.mobile.data.CoTSource.LOCAL,
                    )
                    app.contactStore.ingest(event)
                    reportsSheetOpen = false
                    scope.launch {
                        val xml = soy.engindearing.omnitak.mobile.data.military.MilitaryReportCoT
                            .buildReportEvent(
                                uid = uid,
                                type = result.type,
                                senderCallsign = userPrefs.callsign,
                                lat = pos.latitude,
                                lon = pos.longitude,
                                reportText = result.reportText,
                            )
                        val sent = runCatching { app.serverManager.sendCoT(xml) }.getOrDefault(false)
                        // #171 — fan the report marker out over the active mesh too.
                        runCatching { app.activeMeshManager.sendCoTOverMesh(event) }
                        toast(
                            if (sent) "${result.type.name} sent to server"
                            else "${result.type.name} saved — local only (no server)",
                        )
                    }
                },
                onDismiss = { reportsSheetOpen = false },
            )
        }

        if (teamsPanelOpen) {
            ContactsPanel(
                contacts = contacts.values.toList(),
                onSelect = { c ->
                    panTarget = LatLng(c.lat, c.lon)
                    panTargetTick += 1
                    if (followMeActive) mutatePref { it.copy(followMeActive = false) }
                    teamsPanelOpen = false
                    toast("Panning to ${c.callsign ?: c.uid}")
                },
                onDismiss = { teamsPanelOpen = false },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun buildInProgressDrawing(kind: DrawingKind?, points: List<LatLng>): List<Drawing> {
    if (kind == null || points.isEmpty()) return emptyList()
    return listOf(
        Drawing(
            id = "__in_progress__",
            kind = kind,
            points = points.map { it.latitude to it.longitude },
            colorHex = "#FFC107",  // amber while drafting
        )
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DrawingKindPicker(
    onPick: (DrawingKind) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = soy.engindearing.omnitak.mobile.ui.theme.TacticalSurface,
        title = { androidx.compose.material3.Text("Drawing tool", color = MaterialTheme.colorScheme.onBackground) },
        text = {
            androidx.compose.foundation.layout.Column {
                listOf(
                    DrawingKind.LINE to "Line — connected segments",
                    DrawingKind.POLYGON to "Polygon — closed shape",
                    DrawingKind.CIRCLE to "Circle — center + edge",
                ).forEach { (kind, label) ->
                    androidx.compose.material3.TextButton(
                        onClick = { onPick(kind) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Text(
                            label,
                            color = TacticalAccent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Cancel", color = TacticalAccent)
            }
        },
    )
}

@Composable
private fun DrawingOverlay(
    kind: DrawingKind,
    pointCount: Int,
    onUndo: () -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .padding(top = 76.dp, start = 12.dp, end = 12.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(TacticalBackground.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            "${kind.name.lowercase().replaceFirstChar { it.uppercase() }} · $pointCount pt",
            color = TacticalAccent,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        androidx.compose.material3.TextButton(onClick = onUndo, enabled = pointCount > 0) {
            androidx.compose.material3.Text("Undo", color = TacticalAccent)
        }
        androidx.compose.material3.TextButton(onClick = onCancel) {
            androidx.compose.material3.Text("Cancel", color = TacticalAccent)
        }
        androidx.compose.material3.TextButton(onClick = onFinish) {
            androidx.compose.material3.Text("Save", color = TacticalAccent)
        }
    }
}

@Composable
private fun MeasurementOverlay(
    points: List<LatLng>,
    onUndo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var totalMeters = 0.0
    for (i in 1 until points.size) {
        totalMeters += GeoMath.haversineMeters(
            points[i - 1].latitude, points[i - 1].longitude,
            points[i].latitude, points[i].longitude,
        )
    }
    val bearing = if (points.size >= 2) {
        val a = points[points.size - 2]
        val b = points[points.size - 1]
        GeoMath.bearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)
    } else null

    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .padding(top = 76.dp, start = 12.dp, end = 12.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(TacticalBackground.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            buildString {
                append("${points.size} pt · ")
                append(GeoMath.formatDistance(totalMeters))
                if (bearing != null) append(" · ${GeoMath.formatBearing(bearing)}")
            },
            color = TacticalAccent,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        androidx.compose.material3.TextButton(onClick = onUndo, enabled = points.isNotEmpty()) {
            androidx.compose.material3.Text("Undo", color = TacticalAccent)
        }
        androidx.compose.material3.TextButton(onClick = onClose) {
            androidx.compose.material3.Text("Done", color = TacticalAccent)
        }
    }
}

/** #152 — concentric range rings around [center] as transient native LINE
 *  drawings (no fill). Delegates to [RangeRings] (unit-tested); these never
 *  touch the drawing store, they ride the drawing renderer so they paint on the
 *  GL-buggy Adreno/Mali drivers like every other shape. */
private fun buildRangeRingDrawings(center: LatLng?): List<Drawing> =
    center?.let {
        soy.engindearing.omnitak.mobile.data.rangebearing.RangeRings
            .ringDrawings(it.latitude, it.longitude)
    } ?: emptyList()

/**
 * #152 — range-rings HUD. Mirrors [MeasurementOverlay]: a translucent readout
 * pinned top-start with the center fix + ring legend, a Clear (re-place center)
 * and a Done (exit tool) action.
 */
@Composable
private fun RangeRingsOverlay(
    center: LatLng?,
    distancesMeters: List<Double>,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .padding(top = 76.dp, start = 12.dp, end = 12.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(TacticalBackground.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            if (center == null) {
                "Range rings · tap map to set center"
            } else {
                "Rings ${"%.5f".format(center.latitude)}, ${"%.5f".format(center.longitude)} · " +
                    distancesMeters.joinToString(" · ") {
                        soy.engindearing.omnitak.mobile.data.rangebearing.RangeRings.label(it)
                    }
            },
            color = TacticalAccent,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        androidx.compose.material3.TextButton(onClick = onClear, enabled = center != null) {
            androidx.compose.material3.Text("Clear", color = TacticalAccent)
        }
        androidx.compose.material3.TextButton(onClick = onClose) {
            androidx.compose.material3.Text("Done", color = TacticalAccent)
        }
    }
}

private fun timeLabel(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

/**
 * Standard tactical map control button — translucent dark disc with a
 * tactical-accent glyph. Used for zoom +/− and center-on-me; sized to
 * the same 48dp pip so the column reads as a single control stack.
 */
@Composable
private fun MapControlFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(TacticalBackground.copy(alpha = 0.9f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}
