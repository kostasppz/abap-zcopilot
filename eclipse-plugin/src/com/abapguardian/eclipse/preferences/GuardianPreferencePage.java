package com.abapguardian.eclipse.preferences;

import com.abapguardian.eclipse.Activator;
import com.abapguardian.eclipse.service.GatewayClient;
import com.abapguardian.eclipse.security.SecureCredentialStore;

import java.io.IOException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
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
    private Button testButton;
    private Label connectionStatus;
    private Job connectionTestJob;

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

        boolean tokenLoaded = createApiTokenControls(getFieldEditorParent());
        createConnectionTestControls(getFieldEditorParent(), tokenLoaded);
    }

    private boolean createApiTokenControls(Composite parent) {
        Composite row = new Composite(parent, SWT.NONE);
        GridData rowData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        rowData.horizontalSpan = 2;
        row.setLayoutData(rowData);
        row.setLayout(new GridLayout(4, false));

        Label label = new Label(row, SWT.NONE);
        label.setText("API &token:");

        apiTokenText = new Text(row, SWT.BORDER | SWT.PASSWORD);
        apiTokenText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        var storedToken = credentialStore.getGuardianApiToken();
        storedToken.ifPresent(apiTokenText::setText);

        Button save = new Button(row, SWT.PUSH);
        save.setText("Store securely");
        save.addListener(SWT.Selection, event -> saveApiToken(true));

        Button clear = new Button(row, SWT.PUSH);
        clear.setText("Clear");
        clear.addListener(SWT.Selection, event -> clearApiToken());
        return storedToken.isPresent();
    }

    private void createConnectionTestControls(Composite parent, boolean tokenLoaded) {
        Composite row = new Composite(parent, SWT.NONE);
        GridData rowData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        rowData.horizontalSpan = 2;
        row.setLayoutData(rowData);
        row.setLayout(new GridLayout(2, false));

        testButton = new Button(row, SWT.PUSH);
        testButton.setText("Test Connection");
        testButton.addListener(SWT.Selection, event -> testConnectionAsync());

        connectionStatus = new Label(row, SWT.WRAP);
        connectionStatus.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        connectionStatus.setText(tokenLoaded
                ? "Stored API token loaded from Eclipse Secure Storage."
                : "No API token is stored yet.");
    }

    private void clearApiToken() {
        try {
            credentialStore.remove(SecureCredentialStore.KEY_GUARDIAN_API_TOKEN);
            apiTokenText.setText("");
            showStatus("The stored API token was removed.");
            setErrorMessage(null);
        } catch (IOException | RuntimeException exception) {
            showStorageError("remove", exception);
        }
    }

    private boolean saveApiToken(boolean showConfirmation) {
        try {
            credentialStore.putGuardianApiToken(apiTokenText.getText());
            String message = apiTokenText.getText().isBlank()
                    ? "The stored API token was removed."
                    : "The API token was encrypted and stored successfully.";
            showStatus(message);
            setErrorMessage(null);
            if (showConfirmation) {
                MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
                box.setText("ABAP Guardian");
                box.setMessage(message);
                box.open();
            }
            return true;
        } catch (org.eclipse.equinox.security.storage.StorageException
                | IOException | RuntimeException exception) {
            showStorageError("save", exception);
            return false;
        }
    }

    private void showStorageError(String operation, Throwable exception) {
        Activator.logError("Cannot " + operation + " Guardian API token in Eclipse Secure Storage",
                exception);
        String message = "Eclipse Secure Storage could not " + operation
                + " the API token. Open Window > Show View > Error Log for details.";
        showStatus(message);
        setErrorMessage(message);
        MessageBox box = new MessageBox(getShell(), SWT.ICON_ERROR | SWT.OK);
        box.setText("ABAP Guardian");
        box.setMessage(message);
        box.open();
    }

    private void testConnectionAsync() {
        if (!saveApiToken(false)) {
            return;
        }

        String serviceUrl = serviceUrlEditor.getStringValue();
        int timeoutSeconds = timeoutEditor.getIntValue();
        String apiToken = apiTokenText.getText();
        var display = testButton.getDisplay();

        testButton.setEnabled(false);
        testButton.setText("Testing...");
        showStatus("Testing the Guardian health and authenticated model endpoints...");
        setErrorMessage(null);

        connectionTestJob = new Job("Test ABAP Guardian connection") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                GatewayClient.ConnectionTestResult result;
                try {
                    result = new GatewayClient(serviceUrl, timeoutSeconds, apiToken)
                            .testConnection();
                } catch (RuntimeException exception) {
                    Activator.logError("Unexpected ABAP Guardian connection-test failure", exception);
                    result = GatewayClient.ConnectionTestResult.failed(
                            "The connection test failed unexpectedly. Open the Eclipse Error Log for details.");
                }

                GatewayClient.ConnectionTestResult completedResult = result;
                display.asyncExec(() -> showConnectionTestResult(completedResult));
                return Status.OK_STATUS;
            }
        };
        connectionTestJob.setUser(true);
        connectionTestJob.schedule();
    }

    private void showConnectionTestResult(GatewayClient.ConnectionTestResult result) {
        if (testButton == null || testButton.isDisposed()) {
            return;
        }
        testButton.setEnabled(true);
        testButton.setText("Test Connection");
        connectionTestJob = null;
        showStatus(result.message());
        setErrorMessage(result.healthy() ? null : result.message());

        MessageBox box = new MessageBox(getShell(),
                (result.healthy() ? SWT.ICON_INFORMATION : SWT.ICON_ERROR) | SWT.OK);
        box.setText("ABAP Guardian Connection Test");
        box.setMessage(result.message());
        box.open();
    }

    private void showStatus(String message) {
        if (connectionStatus != null && !connectionStatus.isDisposed()) {
            connectionStatus.setText(message);
            connectionStatus.getParent().layout(true, true);
        }
    }

    @Override
    public boolean performOk() {
        return saveApiToken(false) && super.performOk();
    }

    @Override
    public void dispose() {
        if (connectionTestJob != null) {
            connectionTestJob.cancel();
        }
        super.dispose();
    }
}
