package com.abapguardian.eclipse.preferences;

import com.abapguardian.eclipse.Activator;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.Preferences;

/** Typed access to the plug-in's preferences. */
public final class GuardianPreferences {

    public static final String KEY_SERVICE_URL = "serviceUrl";
    public static final String KEY_TIMEOUT_SECONDS = "timeoutSeconds";
    public static final String KEY_USE_AI = "useAi";
    public static final String KEY_MIN_SEVERITY = "minSeverity";

    /** Hosted proof-of-concept gateway. Override in Preferences for private deployments. */
    public static final String DEFAULT_SERVICE_URL = "https://abap-zcopilot.onrender.com";
    public static final int DEFAULT_TIMEOUT_SECONDS = 120;
    public static final boolean DEFAULT_USE_AI = true;
    public static final String DEFAULT_MIN_SEVERITY = "INFO";

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
}
