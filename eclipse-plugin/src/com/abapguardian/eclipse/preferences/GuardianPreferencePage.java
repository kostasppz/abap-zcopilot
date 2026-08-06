package com.abapguardian.eclipse.preferences;

import com.abapguardian.eclipse.Activator;
import com.abapguardian.eclipse.service.GatewayClient;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.eclipse.core.runtime.preferences.InstanceScope;

/**
 * Preference page: gateway URL, timeout, AI usage, minimum severity, plus a
 * connection test button. Never stores credentials here — anything secret
 * belongs in {@link com.abapguardian.eclipse.security.SecureCredentialStore}.
 */
public class GuardianPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public GuardianPreferencePage() {
        super(GRID);
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, Activator.PLUGIN_ID));
        setDescription("ABAP Guardian analysis service settings. "
                + "The service runs locally by default; no code leaves your machine.");
    }

    @Override
    public void init(IWorkbench workbench) {
        // Nothing to initialize.
    }

    @Override
    protected void createFieldEditors() {
        addField(new StringFieldEditor(GuardianPreferences.KEY_SERVICE_URL,
                "Service &URL:", getFieldEditorParent()));
        addField(new IntegerFieldEditor(GuardianPreferences.KEY_TIMEOUT_SECONDS,
                "Request &timeout (seconds):", getFieldEditorParent(), 4));
        addField(new BooleanFieldEditor(GuardianPreferences.KEY_USE_AI,
                "Use &AI enhancement (local model via gateway)", getFieldEditorParent()));
        addField(new ComboFieldEditor(GuardianPreferences.KEY_MIN_SEVERITY,
                "Minimum &severity to show:",
                new String[][] {
                        {"Info", "INFO"},
                        {"Low", "LOW"},
                        {"Medium", "MEDIUM"},
                        {"High", "HIGH"},
                        {"Critical", "CRITICAL"}},
                getFieldEditorParent()));

        Button testButton = new Button(getFieldEditorParent(), SWT.PUSH);
        testButton.setText("Test Connection");
        testButton.addListener(SWT.Selection, event -> {
            boolean healthy = new GatewayClient().isHealthy();
            MessageBox box = new MessageBox(getShell(),
                    (healthy ? SWT.ICON_INFORMATION : SWT.ICON_ERROR) | SWT.OK);
            box.setText("ABAP Guardian");
            box.setMessage(healthy
                    ? "Gateway is reachable and healthy."
                    : "Gateway is not reachable. Check the service URL and that the gateway is running.");
            box.open();
        });
    }
}
