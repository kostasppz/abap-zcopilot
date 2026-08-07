package com.abapguardian.eclipse.handlers;

import com.abapguardian.eclipse.adapter.AdtEditorAdapter;
import com.abapguardian.eclipse.jobs.AnalyzeJob;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/** Analyzes selected ABAP text without modifying the editor document. */
public class AnalyzeTextSelectionHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        IEditorPart editor = window.getActivePage() == null
                ? null : window.getActivePage().getActiveEditor();
        if (editor == null) {
            MessageDialog.openInformation(window.getShell(), "ABAP Guardian",
                    "Open an ABAP editor and select code first.");
            return null;
        }
        String selection = AdtEditorAdapter.getSelectedText(editor);
        if (selection.isBlank()) {
            MessageDialog.openInformation(window.getShell(), "ABAP Guardian",
                    "Select ABAP code first, or use Analyze Current Editor.");
            return null;
        }
        int lineOffset = AdtEditorAdapter.getSelectionStartLine(editor) - 1;
        new AnalyzeJob(selection, AdtEditorAdapter.getObjectName(editor),
                AdtEditorAdapter.getObjectType(editor), result ->
                Display.getDefault().asyncExec(() ->
                        AnalyzeCurrentHandler.showInFindingsView(
                                window, editor, result.withLineOffset(lineOffset))))
                .schedule();
        return null;
    }
}
