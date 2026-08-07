package com.abapguardian.eclipse.handlers;

import com.abapguardian.eclipse.views.CopilotView;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

/** Requests a preview-only correction for the active ABAP selection. */
public class SuggestCorrectionHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            CopilotView.openWithPrompt(HandlerUtil.getActiveWorkbenchWindowChecked(event),
                    "Suggest a safer or faster alternative for the selected ABAP code. "
                    + "Explain behavior changes and clearly delimit the proposed ABAP code. "
                    + "Do not apply anything automatically.", true);
        } catch (PartInitException exception) {
            throw new ExecutionException("Cannot open ABAP Guardian Copilot", exception);
        }
        return null;
    }
}
