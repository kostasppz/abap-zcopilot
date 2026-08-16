package com.abapguardian.eclipse.preferences;

import com.abapguardian.eclipse.Activator;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.prefs.BackingStoreException;

/** Typed access to the plug-in's preferences. */
public final class GuardianPreferences {

    public static final String KEY_SERVICE_URL = "serviceUrl";
    public static final String KEY_TIMEOUT_SECONDS = "timeoutSeconds";
    public static final String KEY_USE_AI = "useAi";
    public static final String KEY_MIN_SEVERITY = "minSeverity";
    public static final String KEY_LIVE_ANALYSIS = "liveAnalysis";
    public static final String KEY_LIVE_DELAY_MS = "liveDelayMs";
    public static final String KEY_ANALYZE_ON_SAVE = "analyzeOnSave";
    public static final String KEY_LIVE_USE_AI = "liveUseAi";
    public static final String KEY_LAST_WELCOME_VERSION = "lastWelcomeVersion";

    /** RunPod Pod URLs are installation-specific and must be configured explicitly. */
    public static final String DEFAULT_SERVICE_URL = "";
    public static final int DEFAULT_TIMEOUT_SECONDS = 120;
    public static final boolean DEFAULT_USE_AI = true;
    public static final String DEFAULT_MIN_SEVERITY = "INFO";
    public static final boolean DEFAULT_LIVE_ANALYSIS = false;
    public static final int DEFAULT_LIVE_DELAY_MS = 5000;
    public static final boolean DEFAULT_ANALYZE_ON_SAVE = false;
    public static final boolean DEFAULT_LIVE_USE_AI = false;

    private GuardianPreferences() {
    }

    private static Preferences node() {
        return InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
    }

    public static String getServiceUrl() {
        return node().get(KEY_SERVICE_URL, DEFAULT_SERVICE_URL);
    }

    public static int getTimeoutSeconds() {
        return node().getInt(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
    }

    public static boolean isUseAi() {
        return node().getBoolean(KEY_USE_AI, DEFAULT_USE_AI);
    }

    public static String getMinSeverity() {
        return node().get(KEY_MIN_SEVERITY, DEFAULT_MIN_SEVERITY);
    }

    public static boolean isLiveAnalysisEnabled() {
        return node().getBoolean(KEY_LIVE_ANALYSIS, DEFAULT_LIVE_ANALYSIS);
    }

    public static int getLiveDelayMs() {
        return Math.max(1000, node().getInt(KEY_LIVE_DELAY_MS, DEFAULT_LIVE_DELAY_MS));
    }

    public static boolean isAnalyzeOnSave() {
        return node().getBoolean(KEY_ANALYZE_ON_SAVE, DEFAULT_ANALYZE_ON_SAVE);
    }

    public static boolean isLiveUseAi() {
        return node().getBoolean(KEY_LIVE_USE_AI, DEFAULT_LIVE_USE_AI);
    }

    public static String getLastWelcomeVersion() {
        return node().get(KEY_LAST_WELCOME_VERSION, "");
    }

    public static void setLastWelcomeVersion(String version) {
        Preferences preferences = node();
        preferences.put(KEY_LAST_WELCOME_VERSION, version);
        try {
            preferences.flush();
        } catch (BackingStoreException exception) {
            Activator.logError("Cannot persist ABAP Guardian welcome version", exception);
        }
    }
}
