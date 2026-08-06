package com.abapguardian.eclipse.handlers;

import com.abapguardian.eclipse.jobs.AnalyzeJob;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Analyzes the ABAP object currently selected in a navigator/explorer view.
 * Read-only: the selected resource is only read, never modified or saved.
 */
public class AnalyzeSelectedHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (!(selection instanceof IStructuredSelection structured) || structured.isEmpty()) {
            MessageDialog.openInformation(window.getShell(), "ABAP Guardian",
                    "Select an ABAP source file first.");
            return null;
        }
        Object element = structured.getFirstElement();
        IFile file = adaptToFile(element);
        if (file == null) {
            MessageDialog.openInformation(window.getShell(), "ABAP Guardian",
                    "The selection is not a readable source file.");
            return null;
        }
        String source;
        try (InputStream in = file.getContents()) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | org.eclipse.core.runtime.CoreException e) {
            throw new ExecutionException("Cannot read selected file", e);
        }
        new AnalyzeJob(source, file.getName(), "PROG", result ->
                Display.getDefault().asyncExec(
                        () -> AnalyzeCurrentHandler.showInFindingsView(window, result))
        ).schedule();
        return null;
    }

    private IFile adaptToFile(Object element) {
        if (element instanceof IFile file) {
            return file;
        }
        if (element instanceof org.eclipse.core.runtime.IAdaptable adaptable) {
            return adaptable.getAdapter(IFile.class);
        }
        return null;
    }
}
