package com.abapguardian.eclipse.handlers;

import com.abapguardian.eclipse.views.FindingsView;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

/** Opens the Guardian Findings view. */
public class OpenFindingsHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        try {
            window.getActivePage().showView(FindingsView.ID);
        } catch (PartInitException e) {
            throw new ExecutionException("Cannot open Guardian Findings view", e);
        }
        return null;
    }
}
