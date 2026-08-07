package com.abapguardian.eclipse.handlers;

import com.abapguardian.eclipse.views.CopilotView;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

/** Opens the docked ABAP Guardian Copilot view. */
public class OpenCopilotHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            CopilotView.open(HandlerUtil.getActiveWorkbenchWindowChecked(event));
        } catch (PartInitException exception) {
            throw new ExecutionException("Cannot open ABAP Guardian Copilot", exception);
        }
        return null;
    }
}
