package com.abapguardian.eclipse.preferences;

import com.abapguardian.eclipse.Activator;
import com.abapguardian.eclipse.service.GatewayClient;
import com.abapguardian.eclipse.security.SecureCredentialStore;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.eclipse.core.runtime.preferences.InstanceScope;

/**
 * Preference page: gateway URL, timeout, AI usage, minimum severity, plus a
 * connection test button. The API token is written only through
 * {@link com.abapguardian.eclipse.security.SecureCredentialStore}; it never
 * enters the ordinary preference store.
 */
public class GuardianPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private final SecureCredentialStore credentialStore = new SecureCredentialStore();
    private Text apiTokenText;
    private StringFieldEditor serviceUrlEditor;
    private IntegerFieldEditor timeoutEditor;

    public GuardianPreferencePage() {
        super(GRID);
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, Activator.PLUGIN_ID));
        setDescription("ABAP Guardian RunPod analysis service settings. "
                + "Deterministic analysis runs on the service; when AI is enabled, "
                + "a redacted code snippet is processed by the private ABAP Expert model on RunPod.");
    }

    @Override
    public void init(IWorkbench workbench) {
        // Nothing to initialize.
    }

    @Override
    protected void createFieldEditors() {
        serviceUrlEditor = new StringFieldEditor(GuardianPreferences.KEY_SERVICE_URL,
                "Service &URL:", getFieldEditorParent());
        addField(serviceUrlEditor);
        timeoutEditor = new IntegerFieldEditor(GuardianPreferences.KEY_TIMEOUT_SECONDS,
                "Request &timeout (seconds):", getFieldEditorParent(), 4);
        addField(timeoutEditor);
        addField(new BooleanFieldEditor(GuardianPreferences.KEY_USE_AI,
                "Use &online AI enhancement", getFieldEditorParent()));
        addField(new BooleanFieldEditor(GuardianPreferences.KEY_LIVE_ANALYSIS,
                "Enable live analysis after a typing pause", getFieldEditorParent()));
        IntegerFieldEditor delay = new IntegerFieldEditor(GuardianPreferences.KEY_LIVE_DELAY_MS,
                "Live analysis delay (milliseconds):", getFieldEditorParent(), 6);
        delay.setValidRange(1000, 60000);
        addField(delay);
        addField(new BooleanFieldEditor(GuardianPreferences.KEY_ANALYZE_ON_SAVE,
                "Analyze automatically when the editor is saved", getFieldEditorParent()));
        addField(new BooleanFieldEditor(GuardianPreferences.KEY_LIVE_USE_AI,
                "Use online AI during automatic analysis (may consume API quota)",
                getFieldEditorParent()));
        addField(new ComboFieldEditor(GuardianPreferences.KEY_MIN_SEVERITY,
                "Minimum &severity to show:",
                new String[][] {
                        {"Info", "INFO"},
                        {"Low", "LOW"},
                        {"Medium", "MEDIUM"},
                        {"High", "HIGH"},
                        {"Critical", "CRITICAL"}},
                getFieldEditorParent()));

        createApiTokenControls(getFieldEditorParent());

        Button testButton = new Button(getFieldEditorParent(), SWT.PUSH);
        testButton.setText("Test Connection");
        testButton.addListener(SWT.Selection, event -> {
            if (!saveApiToken(false)) {
                return;
            }
            boolean healthy = new GatewayClient(
                    serviceUrlEditor.getStringValue(),
                    timeoutEditor.getIntValue(),
                    apiTokenText.getText()).isHealthy();
            MessageBox box = new MessageBox(getShell(),
                    (healthy ? SWT.ICON_INFORMATION : SWT.ICON_ERROR) | SWT.OK);
            box.setText("ABAP Guardian");
            box.setMessage(healthy
                    ? "ABAP Guardian is reachable and the API token is accepted."
                    : "Connection failed. Check the service URL, API token and deployment status.");
            box.open();
        });
    }

    private void createApiTokenControls(Composite parent) {
        Composite row = new Composite(parent, SWT.NONE);
        GridData rowData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        rowData.horizontalSpan = 2;
        row.setLayoutData(rowData);
        row.setLayout(new GridLayout(4, false));

        Label label = new Label(row, SWT.NONE);
        label.setText("API &token:");

        apiTokenText = new Text(row, SWT.BORDER | SWT.PASSWORD);
        apiTokenText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        credentialStore.getGuardianApiToken().ifPresent(apiTokenText::setText);

        Button save = new Button(row, SWT.PUSH);
        save.setText("Store securely");
        save.addListener(SWT.Selection, event -> saveApiToken(true));

        Button clear = new Button(row, SWT.PUSH);
        clear.setText("Clear");
        clear.addListener(SWT.Selection, event -> {
            credentialStore.remove(SecureCredentialStore.KEY_GUARDIAN_API_TOKEN);
            apiTokenText.setText("");
        });
    }

    private boolean saveApiToken(boolean showConfirmation) {
        try {
            credentialStore.putGuardianApiToken(apiTokenText.getText());
            if (showConfirmation) {
                MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
                box.setText("ABAP Guardian");
                box.setMessage(apiTokenText.getText().isBlank()
                        ? "The stored API token was removed."
                        : "The API token was stored in Eclipse Secure Storage.");
                box.open();
            }
            return true;
        } catch (org.eclipse.equinox.security.storage.StorageException exception) {
            MessageBox box = new MessageBox(getShell(), SWT.ICON_ERROR | SWT.OK);
            box.setText("ABAP Guardian");
            box.setMessage("Eclipse Secure Storage could not save the API token.");
            box.open();
            return false;
        }
    }

    @Override
    public boolean performOk() {
        return saveApiToken(false) && super.performOk();
    }
}
