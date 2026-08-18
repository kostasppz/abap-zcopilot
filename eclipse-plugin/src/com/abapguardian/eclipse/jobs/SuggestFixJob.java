package com.abapguardian.eclipse.jobs;

import com.abapguardian.eclipse.Activator;
import com.abapguardian.eclipse.api.GuardianFinding;
import com.abapguardian.eclipse.service.GatewayClient;
import com.abapguardian.eclipse.ui.GuardianUiState;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import java.util.function.Consumer;

/** Generates one preview-only ABAP replacement without blocking the UI. */
public final class SuggestFixJob extends Job {

    private final GuardianFinding finding;
    private final String sourceSnippet;
    private final Consumer<GatewayClient.SuggestedFixResult> onSuccess;
    private final Consumer<String> onFailure;

    public SuggestFixJob(GuardianFinding finding, String sourceSnippet,
                         Consumer<GatewayClient.SuggestedFixResult> onSuccess,
                         Consumer<String> onFailure) {
        super("ABAP Guardian: generating fix for " + finding.getRuleId());
        this.finding = finding;
        this.sourceSnippet = sourceSnippet;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
        setUser(true);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Generating replacement ABAP code", IProgressMonitor.UNKNOWN);
        GuardianUiState.set(GuardianUiState.Kind.ANALYZING,
                "Generating a suggested fix for " + finding.getRuleId() + "…");
        try {
            GatewayClient.SuggestedFixResult result =
                    new GatewayClient().suggestFix(finding, sourceSnippet);
            if (monitor.isCanceled()) {
                return Status.CANCEL_STATUS;
            }
            onSuccess.accept(result);
            GuardianUiState.set(GuardianUiState.Kind.SUCCESS,
                    "Suggested fix ready for review");
            return Status.OK_STATUS;
        } catch (GatewayClient.GatewayException exception) {
            GuardianUiState.set(GuardianUiState.Kind.ERROR, exception.getMessage());
            onFailure.accept(exception.getMessage());
            return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
                    exception.getMessage(), exception);
        } finally {
            monitor.done();
        }
    }
}
