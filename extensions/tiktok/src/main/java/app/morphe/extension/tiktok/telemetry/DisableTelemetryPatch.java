/*
 * SPDX-FileCopyrightText: 2026 hxreborn
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.morphe.extension.tiktok.telemetry;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

public final class DisableTelemetryPatch {
    private DisableTelemetryPatch() {}

    public static boolean isTelemetryDisabled() {
        // Keep analytics enabled if the context isn't initialized yet
        return Utils.getContext() != null && Settings.DISABLE_ANALYTICS.get();
    }
}
