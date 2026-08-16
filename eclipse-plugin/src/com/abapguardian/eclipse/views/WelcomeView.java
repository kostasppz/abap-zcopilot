package com.abapguardian.eclipse.views;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.ViewPart;

/** Welcome and What's New page, shown once for each installed version. */
public class WelcomeView extends ViewPart {

    public static final String ID = "com.abapguardian.eclipse.views.welcome";

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        Label title = new Label(parent, SWT.NONE);
        title.setText("Welcome to ABAP Guardian Copilot");
        title.setFont(parent.getDisplay().getSystemFont());

        Label version = new Label(parent, SWT.NONE);
        var bundle = Platform.getBundle("com.abapguardian.eclipse.plugin");
        version.setText("Installed version: "
                + (bundle == null ? "unknown" : bundle.getVersion().toString()));

        Label intro = new Label(parent, SWT.WRAP);
        intro.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        intro.setText(
                "ABAP Guardian analyzes active ABAP source for performance, security, "
                + "S/4HANA compatibility, Clean ABAP, privacy and policy problems. "
                + "Copilot can answer questions using the "
                + "active editor and the project's bundled knowledge. Source context is "
                + "sent only when you explicitly use chat or enable automatic analysis.");

        Label whatsNew = new Label(parent, SWT.WRAP);
        whatsNew.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        whatsNew.setText(
                "What's new in 0.5.0\n\n"
                + "• SAP S/4HANA compatibility checks and migration suggestions\n"
                + "• ABAP Clean Code checks and modernization suggestions\n"
                + "• Separate Performance, Security, S/4HANA and Clean Code commands\n"
                + "• Private RunPod ABAP Expert integration\n"
                + "• Welcome page now opens once per installed version");

        Composite actions = new Composite(parent, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        actions.setLayout(new GridLayout(3, false));
        Button openCopilot = new Button(actions, SWT.PUSH);
        openCopilot.setText("Open Copilot");
        openCopilot.addListener(SWT.Selection, event -> {
            try {
                CopilotView.open(getSite().getWorkbenchWindow());
            } catch (org.eclipse.ui.PartInitException exception) {
                com.abapguardian.eclipse.Activator.logError("Cannot open Copilot", exception);
            }
        });
        Button settings = new Button(actions, SWT.PUSH);
        settings.setText("Privacy and Live Analysis Settings");
        settings.addListener(SWT.Selection, event ->
                PreferencesUtil.createPreferenceDialogOn(getSite().getShell(),
                        "com.abapguardian.eclipse.preferences.page",
                        new String[] {"com.abapguardian.eclipse.preferences.page"}, null).open());
        Button close = new Button(actions, SWT.PUSH);
        close.setText("Close Welcome");
        close.addListener(SWT.Selection, event ->
                getSite().getWorkbenchWindow().getActivePage().hideView(this));
    }

    @Override
    public void setFocus() {
        // The page contains buttons; SWT handles traversal naturally.
    }
}
