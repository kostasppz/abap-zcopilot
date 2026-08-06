package com.abapguardian.eclipse;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator. Uses only public Eclipse Platform APIs — no
 * {@code .internal} packages anywhere in this plug-in.
 */
public class Activator implements BundleActivator {

    public static final String PLUGIN_ID = "com.abapguardian.eclipse.plugin";

    private static Activator instance;
    private BundleContext context;

    @Override
    public void start(BundleContext bundleContext) {
        instance = this;
        this.context = bundleContext;
    }

    @Override
    public void stop(BundleContext bundleContext) {
        instance = null;
        this.context = null;
    }

    public static Activator getDefault() {
        return instance;
    }

    public BundleContext getContext() {
        return context;
    }

    public static void logError(String message, Throwable t) {
        ILog log = Platform.getLog(Platform.getBundle(PLUGIN_ID));
        log.log(new Status(Status.ERROR, PLUGIN_ID, message, t));
    }

    public static void logInfo(String message) {
        ILog log = Platform.getLog(Platform.getBundle(PLUGIN_ID));
        log.log(new Status(Status.INFO, PLUGIN_ID, message));
    }
}
