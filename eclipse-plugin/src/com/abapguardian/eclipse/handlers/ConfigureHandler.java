package com.abapguardian.eclipse.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.handlers.HandlerUtil;

/** Opens the ABAP Guardian preference page. */
public class ConfigureHandler extends AbstractHandler {

    private static final String PAGE_ID = "com.abapguardian.eclipse.preferences.page";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        PreferencesUtil.createPreferenceDialogOn(window.getShell(), PAGE_ID,
                new String[] {PAGE_ID}, null).open();
        return null;
    }
}
