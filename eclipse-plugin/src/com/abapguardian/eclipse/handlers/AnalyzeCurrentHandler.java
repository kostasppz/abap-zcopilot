package com.abapguardian.eclipse.handlers;

import com.abapguardian.eclipse.adapter.AdtEditorAdapter;
import com.abapguardian.eclipse.jobs.AnalyzeJob;
import com.abapguardian.eclipse.views.FindingsView;
import com.abapguardian.eclipse.ui.FindingAnnotations;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import java.util.Optional;
import java.util.List;

/**
 * Analyzes the source of the active editor. Runs as a background Job and
 * never saves or activates the object — analysis is strictly read-only.
 */
public class AnalyzeCurrentHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        IEditorPart editor = window.getActivePage() != null
                ? window.getActivePage().getActiveEditor() : null;
        if (editor == null) {
            MessageDialog.openInformation(window.getShell(), "ABAP Guardian",
                    "No active editor. Open an ABAP source first.");
            return null;
        }
        analyze(window, editor);
        return null;
    }

    /** Runs a manual analysis for an editor and updates both view and markers. */
    public static void analyze(IWorkbenchWindow window, IEditorPart editor) {
        analyze(window, editor, List.of());
    }

    /** Runs only the requested rule categories; an empty list means all checks. */
    public static void analyze(IWorkbenchWindow window, IEditorPart editor,
                               List<String> categories) {
        Optional<String> source = AdtEditorAdapter.getSource(editor);
        if (source.isEmpty()) {
            MessageDialog.openInformation(window.getShell(), "ABAP Guardian",
                    "The active editor does not provide readable text content.");
            return;
        }
        String objectName = AdtEditorAdapter.getObjectName(editor);
        String objectType = AdtEditorAdapter.getObjectType(editor);

        new AnalyzeJob(source.get(), objectName, objectType, categories, result ->
                Display.getDefault().asyncExec(() -> showInFindingsView(window, editor, result))
        ).schedule();
    }

    static void showInFindingsView(IWorkbenchWindow window,
                                   com.abapguardian.eclipse.api.GuardianAnalysisResult result) {
        IEditorPart editor = window.getActivePage() == null
                ? null : window.getActivePage().getActiveEditor();
        showInFindingsView(window, editor, result);
    }

    public static void showInFindingsView(IWorkbenchWindow window, IEditorPart editor,
                                          com.abapguardian.eclipse.api.GuardianAnalysisResult result) {
        showInFindingsView(window, editor, result, true);
    }

    public static void showInFindingsView(IWorkbenchWindow window, IEditorPart editor,
                                          com.abapguardian.eclipse.api.GuardianAnalysisResult result,
                                          boolean activateView) {
        try {
            IWorkbenchPage page = window.getActivePage();
            if (page == null) {
                return;
            }
            FindingsView view = (FindingsView) page.showView(FindingsView.ID, null,
                    activateView ? IWorkbenchPage.VIEW_ACTIVATE : IWorkbenchPage.VIEW_VISIBLE);
            view.showResult(result);
            if (editor != null) {
                AdtEditorAdapter.getDocument(editor).ifPresent(document ->
                        AdtEditorAdapter.getAnnotationModel(editor).ifPresent(model ->
                                FindingAnnotations.apply(model, document, result.getFindings())));
            }
        } catch (PartInitException e) {
            com.abapguardian.eclipse.Activator.logError("Cannot open findings view", e);
        }
    }
}
