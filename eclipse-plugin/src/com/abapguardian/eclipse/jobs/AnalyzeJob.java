package com.abapguardian.eclipse.jobs;

import com.abapguardian.eclipse.Activator;
import com.abapguardian.eclipse.api.GuardianAnalysisResult;
import com.abapguardian.eclipse.preferences.GuardianPreferences;
import com.abapguardian.eclipse.service.GatewayClient;
import com.abapguardian.eclipse.ui.GuardianUiState;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import java.util.function.Consumer;

/**
 * Background job running one analysis via the gateway. The UI thread is
 * never blocked; results are delivered to a consumer on completion.
 */
public class AnalyzeJob extends Job {

    private final String source;
    private final String objectName;
    private final String objectType;
    private final Consumer<GuardianAnalysisResult> onSuccess;
    private final Consumer<String> onFailure;
    private final boolean useAi;

    public AnalyzeJob(String source, String objectName, String objectType,
                      Consumer<GuardianAnalysisResult> onSuccess) {
        this(source, objectName, objectType, GuardianPreferences.isUseAi(), true,
                onSuccess, message -> { });
    }

    public AnalyzeJob(String source, String objectName, String objectType,
                      boolean useAi, boolean userInitiated,
                      Consumer<GuardianAnalysisResult> onSuccess,
                      Consumer<String> onFailure) {
        super("ABAP Guardian: analyzing " + objectName);
        this.source = source;
        this.objectName = objectName;
        this.objectType = objectType;
        this.useAi = useAi;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
        setUser(userInitiated);
        setSystem(!userInitiated);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Contacting analysis service", IProgressMonitor.UNKNOWN);
        GuardianUiState.set(GuardianUiState.Kind.ANALYZING,
                "Analyzing " + objectName + "…");
        try {
            GatewayClient client = new GatewayClient();
            GuardianAnalysisResult result = client.analyze(
                    source, objectName, objectType, useAi);
            if (monitor.isCanceled()) {
                return Status.CANCEL_STATUS;
            }
            onSuccess.accept(result);
            GuardianUiState.set(GuardianUiState.Kind.SUCCESS,
                    result.getFindings().size() + " finding(s) in " + objectName);
            return Status.OK_STATUS;
        } catch (GatewayClient.GatewayException e) {
            GuardianUiState.set(GuardianUiState.Kind.ERROR, e.getMessage());
            onFailure.accept(e.getMessage());
            return new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e);
        } finally {
            monitor.done();
        }
    }
}
