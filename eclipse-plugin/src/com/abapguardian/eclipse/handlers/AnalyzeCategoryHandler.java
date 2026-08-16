package com.abapguardian.eclipse.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import java.util.List;

/** Runs the active editor through exactly one analysis category. */
public class AnalyzeCategoryHandler extends AbstractHandler {

    public static final String PERFORMANCE_ID =
            "com.abapguardian.eclipse.commands.analyzePerformance";
    public static final String SECURITY_ID =
            "com.abapguardian.eclipse.commands.analyzeSecurity";
    public static final String S4HANA_ID =
            "com.abapguardian.eclipse.commands.analyzeS4Hana";
    public static final String CLEAN_CODE_ID =
            "com.abapguardian.eclipse.commands.analyzeCleanCode";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        IEditorPart editor = window.getActivePage() == null
                ? null : window.getActivePage().getActiveEditor();
        if (editor == null) {
            MessageDialog.openInformation(window.getShell(), "ABAP Guardian",
                    "No active editor. Open an ABAP source first.");
            return null;
        }
        AnalyzeCurrentHandler.analyze(window, editor,
                List.of(categoryFor(event.getCommand().getId())));
        return null;
    }

    private String categoryFor(String commandId) throws ExecutionException {
        return switch (commandId) {
            case PERFORMANCE_ID -> "PERFORMANCE";
            case SECURITY_ID -> "SECURITY";
            case S4HANA_ID -> "S4HANA";
            case CLEAN_CODE_ID -> "CLEAN_CODE";
            default -> throw new ExecutionException("Unknown Guardian analysis command: " + commandId);
        };
    }
}
