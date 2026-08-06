package com.abapguardian.eclipse.jobs;

import com.abapguardian.eclipse.Activator;
import com.abapguardian.eclipse.api.GuardianAnalysisResult;
import com.abapguardian.eclipse.preferences.GuardianPreferences;
import com.abapguardian.eclipse.service.GatewayClient;

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

    public AnalyzeJob(String source, String objectName, String objectType,
                      Consumer<GuardianAnalysisResult> onSuccess) {
        super("ABAP Guardian: analyzing " + objectName);
        this.source = source;
        this.objectName = objectName;
        this.objectType = objectType;
        this.onSuccess = onSuccess;
        setUser(true);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Contacting analysis service", IProgressMonitor.UNKNOWN);
        try {
            GatewayClient client = new GatewayClient();
            GuardianAnalysisResult result = client.analyze(
                    source, objectName, objectType, GuardianPreferences.isUseAi());
            if (monitor.isCanceled()) {
                return Status.CANCEL_STATUS;
            }
            onSuccess.accept(result);
            return Status.OK_STATUS;
        } catch (GatewayClient.GatewayException e) {
            return new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e);
        } finally {
            monitor.done();
        }
    }
}
