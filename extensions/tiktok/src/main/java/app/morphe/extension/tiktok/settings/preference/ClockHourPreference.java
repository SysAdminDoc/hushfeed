/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;

import app.morphe.extension.shared.settings.IntegerSetting;

import java.util.Locale;

/**
 * An hour of the day rather than a count of things. "Current: 13 o'clock" is not how anyone says
 * it, and "Current: 0 o'clock" is not how anyone says anything.
 */
@SuppressWarnings("deprecation")
public class ClockHourPreference extends NumberInputPreference {
    public ClockHourPreference(Context context, String title, String summary, IntegerSetting setting) {
        super(context, title, summary, setting, "", "");
    }

    @Override
    protected String displayValue(int value) {
        return String.format(Locale.getDefault(), "%02d:00", value);
    }
}
