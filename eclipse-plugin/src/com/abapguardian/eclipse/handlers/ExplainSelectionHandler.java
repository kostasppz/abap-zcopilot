package com.abapguardian.eclipse.handlers;

import com.abapguardian.eclipse.views.CopilotView;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

/** Sends the active ABAP selection to Copilot for an explanation. */
public class ExplainSelectionHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            CopilotView.openWithPrompt(HandlerUtil.getActiveWorkbenchWindowChecked(event),
                    "Explain the selected ABAP code, including its behavior and risks.", true);
        } catch (PartInitException exception) {
            throw new ExecutionException("Cannot open ABAP Guardian Copilot", exception);
        }
        return null;
    }
}
