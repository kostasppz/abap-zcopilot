package com.abapguardian.eclipse.preferences;

import com.abapguardian.eclipse.Activator;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.osgi.service.prefs.Preferences;

/** Supplies default values for all Guardian preferences. */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        Preferences defaults = DefaultScope.INSTANCE.getNode(Activator.PLUGIN_ID);
        defaults.put(GuardianPreferences.KEY_SERVICE_URL, GuardianPreferences.DEFAULT_SERVICE_URL);
        defaults.putInt(GuardianPreferences.KEY_TIMEOUT_SECONDS, GuardianPreferences.DEFAULT_TIMEOUT_SECONDS);
        defaults.putBoolean(GuardianPreferences.KEY_USE_AI, GuardianPreferences.DEFAULT_USE_AI);
        defaults.put(GuardianPreferences.KEY_MIN_SEVERITY, GuardianPreferences.DEFAULT_MIN_SEVERITY);
        defaults.putBoolean(GuardianPreferences.KEY_LIVE_ANALYSIS,
                GuardianPreferences.DEFAULT_LIVE_ANALYSIS);
        defaults.putInt(GuardianPreferences.KEY_LIVE_DELAY_MS,
                GuardianPreferences.DEFAULT_LIVE_DELAY_MS);
        defaults.putBoolean(GuardianPreferences.KEY_ANALYZE_ON_SAVE,
                GuardianPreferences.DEFAULT_ANALYZE_ON_SAVE);
        defaults.putBoolean(GuardianPreferences.KEY_LIVE_USE_AI,
                GuardianPreferences.DEFAULT_LIVE_USE_AI);
    }
}
