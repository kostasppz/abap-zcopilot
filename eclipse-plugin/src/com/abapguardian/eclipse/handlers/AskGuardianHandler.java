package com.abapguardian.eclipse.handlers;

import com.abapguardian.eclipse.views.CopilotView;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

/** Opens Copilot with a neutral question prompt and active editor context enabled. */
public class AskGuardianHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            CopilotView.openWithPrompt(HandlerUtil.getActiveWorkbenchWindowChecked(event), "", false);
        } catch (PartInitException exception) {
            throw new ExecutionException("Cannot open ABAP Guardian Copilot", exception);
        }
        return null;
    }
}
