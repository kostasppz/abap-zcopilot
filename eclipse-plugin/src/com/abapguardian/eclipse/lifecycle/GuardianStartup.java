package com.abapguardian.eclipse.lifecycle;

import com.abapguardian.eclipse.Activator;
import com.abapguardian.eclipse.preferences.GuardianPreferences;
import com.abapguardian.eclipse.views.WelcomeView;
import com.abapguardian.eclipse.service.GatewayClient;
import com.abapguardian.eclipse.ui.GuardianUiState;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/** Installs editor listeners and opens Welcome/What's New once per version. */
public class GuardianStartup implements IStartup {

    @Override
    public void earlyStartup() {
        Display.getDefault().asyncExec(() -> {
            for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
                LiveAnalysisController.install(window);
            }
            PlatformUI.getWorkbench().addWindowListener(new IWindowListener() {
                @Override
                public void windowOpened(IWorkbenchWindow window) {
                    LiveAnalysisController.install(window);
                }

                @Override public void windowActivated(IWorkbenchWindow window) { }
                @Override public void windowDeactivated(IWorkbenchWindow window) { }
                @Override public void windowClosed(IWorkbenchWindow window) { }
            });
            checkService();
            showWelcomeWhenVersionChanged();
        });
    }

    private void checkService() {
        Job job = new Job("ABAP Guardian service check") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                boolean healthy = new GatewayClient().isHealthy();
                GuardianUiState.set(healthy ? GuardianUiState.Kind.READY
                                : GuardianUiState.Kind.OFFLINE,
                        healthy ? "Service connected" : "Service offline");
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule();
    }

    private void showWelcomeWhenVersionChanged() {
        var bundle = Platform.getBundle(Activator.PLUGIN_ID);
        String version = bundle == null ? "unknown" : bundle.getVersion().toString();
        if (version.equals(GuardianPreferences.getLastWelcomeVersion())) {
            return;
        }
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null || window.getActivePage() == null) {
            return;
        }
        try {
            window.getActivePage().showView(WelcomeView.ID);
            GuardianPreferences.setLastWelcomeVersion(version);
        } catch (org.eclipse.ui.PartInitException exception) {
            Activator.logError("Cannot open ABAP Guardian Welcome", exception);
        }
    }
}
