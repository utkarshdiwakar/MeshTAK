/*
 * Copyright (c) 2026 MeshTAK contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package soy.engindearing.omnitak.mobile.host

import android.app.Application

/**
 * One-time process init the tactical map needs, mirroring the calls the
 * original NodeCast Application.onCreate performed. The host app must call
 * this from its own Application.onCreate BEFORE the first MapView composes.
 */
object TacMapInit {
    fun init(app: Application) {
        // MapLibre HTTP client with an app-identifying User-Agent — OSM
        // serves 403 tiles to generic UAs. Internally runs
        // MapLibre.getInstance first, which is why this must be first here.
        soy.engindearing.omnitak.mobile.data.MapTileHttp.install(app)
        // Localized strings used across the ported sheets.
        soy.engindearing.omnitak.mobile.i18n.Loc.init(app)
        // CoT-type → SIDC catalogue from assets/cot_types.json; silent
        // failure keeps the hardcoded floor so the map still renders.
        soy.engindearing.omnitak.mobile.data.symbology.MilStdIconService.loadFromAssets(app)
    }
}
