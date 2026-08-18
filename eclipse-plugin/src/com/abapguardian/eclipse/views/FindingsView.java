package com.abapguardian.eclipse.views;

import com.abapguardian.eclipse.adapter.AdtEditorAdapter;
import com.abapguardian.eclipse.api.GuardianAnalysisResult;
import com.abapguardian.eclipse.api.GuardianFinding;
import com.abapguardian.eclipse.jobs.SuggestFixJob;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import com.abapguardian.eclipse.ui.SuggestedFixDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * "Guardian Findings" view: sortable table with the columns
 * Severity | Category | Rule | Line | Confidence | Title.
 * Double-click navigates to the finding's line in the active editor.
 */
public class FindingsView extends ViewPart {

    public static final String ID = "com.abapguardian.eclipse.views.findings";

    private TableViewer viewer;
    private final List<GuardianFinding> findings = new ArrayList<>();
    private IEditorPart sourceEditor;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new FillLayout());
        viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        addColumn("Severity", 80, f -> f.getSeverity());
        addColumn("Category", 100, f -> f.getCategory());
        addColumn("Rule", 240, f -> f.getRuleId());
        addColumn("Line", 60, f -> String.valueOf(f.getStartLine()));
        addColumn("Confidence", 90, f -> String.format("%.0f%%", f.getConfidence() * 100));
        addColumn("Title", 320, GuardianFinding::getTitle);
        addColumn("Description", 520, GuardianFinding::getExplanation);
        addColumn("Suggestion", 520, f ->
                f.getSuggestedCode() != null && !f.getSuggestedCode().isBlank()
                        ? f.getSuggestedCode()
                        : f.getRecommendation()
                                + "  [Right-click → Generate & Review Suggested Fix…]");

        viewer.setInput(findings);
        viewer.addDoubleClickListener(event -> {
            IStructuredSelection selection = viewer.getStructuredSelection();
            if (selection.getFirstElement() instanceof GuardianFinding finding) {
                navigateTo(finding);
            }
        });
        createContextMenu();
    }

    private void createContextMenu() {
        MenuManager manager = new MenuManager();
        manager.add(new Action("Generate & Review Suggested Fix…") {
            @Override
            public void run() {
                GuardianFinding finding = selectedFinding();
                IEditorPart editor = resultEditor();
                if (finding != null && editor != null) {
                    reviewOrGenerateFix(editor, finding);
                }
            }
        });
        manager.add(new Action("Go to Finding") {
            @Override
            public void run() {
                GuardianFinding finding = selectedFinding();
                if (finding != null) {
                    navigateTo(finding);
                }
            }
        });
        Menu menu = manager.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);
        getSite().registerContextMenu(manager, viewer);
    }

    private GuardianFinding selectedFinding() {
        Object selected = viewer.getStructuredSelection().getFirstElement();
        return selected instanceof GuardianFinding finding ? finding : null;
    }

    private IEditorPart resultEditor() {
        IWorkbenchPage page = getSite().getWorkbenchWindow().getActivePage();
        if (sourceEditor != null && page != null
                && page.findEditor(sourceEditor.getEditorInput()) == sourceEditor) {
            return sourceEditor;
        }
        return page == null ? null : page.getActiveEditor();
    }

    private void reviewOrGenerateFix(IEditorPart editor, GuardianFinding finding) {
        if (finding.getSuggestedCode() != null && !finding.getSuggestedCode().isBlank()) {
            SuggestedFixDialog.proposeFix(getSite().getShell(), editor, finding);
            return;
        }
        String snippet = AdtEditorAdapter.getSourceSnippet(editor,
                finding.getStartLine(), finding.getEndLine(), 0, 4000);
        if (snippet.isBlank()) {
            org.eclipse.jface.dialogs.MessageDialog.openInformation(getSite().getShell(),
                    "ABAP Guardian",
                    "The analyzed source is no longer available. Re-run the analysis first.");
            return;
        }
        new SuggestFixJob(finding, snippet,
                generated -> Display.getDefault().asyncExec(() -> {
                    if (viewer == null || viewer.getControl().isDisposed()) {
                        return;
                    }
                    if (!findings.contains(finding)) {
                        return;
                    }
                    String currentSnippet = AdtEditorAdapter.getSourceSnippet(editor,
                            finding.getStartLine(), finding.getEndLine(), 0, 4000);
                    if (!snippet.equals(currentSnippet)) {
                        org.eclipse.jface.dialogs.MessageDialog.openInformation(
                                getSite().getShell(), "ABAP Guardian",
                                "The source changed while the fix was generated. "
                                        + "Re-run the analysis before applying it.");
                        return;
                    }
                    GuardianFinding enriched = finding.withSuggestedCode(
                            generated.suggestedCode(), generated.requiresHumanReview());
                    if (!replaceFinding(finding, enriched)) {
                        return;
                    }
                    SuggestedFixDialog.proposeFix(getSite().getShell(), editor, enriched,
                            generated.caveats());
                }),
                message -> Display.getDefault().asyncExec(() -> {
                    if (viewer != null && !viewer.getControl().isDisposed()) {
                        org.eclipse.jface.dialogs.MessageDialog.openError(getSite().getShell(),
                                "ABAP Guardian — Suggested Fix", message);
                    }
                })).schedule();
    }

    private boolean replaceFinding(GuardianFinding original, GuardianFinding replacement) {
        int index = findings.indexOf(original);
        if (index >= 0) {
            findings.set(index, replacement);
            viewer.refresh();
            viewer.setSelection(new org.eclipse.jface.viewers.StructuredSelection(replacement), true);
            return true;
        }
        return false;
    }

    private interface FindingText {
        String get(GuardianFinding finding);
    }

    private void addColumn(String header, int width, FindingText text) {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(header);
        column.getColumn().setWidth(width);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return element instanceof GuardianFinding f ? text.get(f) : "";
            }
        });
    }

    /** Replaces the table content with the given analysis result. */
    public void showResult(GuardianAnalysisResult result) {
        showResult(result, null);
    }

    /** Replaces the table and remembers the editor that produced the result. */
    public void showResult(GuardianAnalysisResult result, IEditorPart editor) {
        sourceEditor = editor;
        findings.clear();
        findings.addAll(result.getFindings());
        if (viewer != null && !viewer.getTable().isDisposed()) {
            viewer.refresh();
        }
    }

    private void navigateTo(GuardianFinding finding) {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (page == null) {
            return;
        }
        IEditorPart editor = resultEditor();
        if (editor != null) {
            page.activate(editor);
            AdtEditorAdapter.revealLine(editor, finding.getStartLine());
        }
    }

    @Override
    public void setFocus() {
        if (viewer != null) {
            viewer.getControl().setFocus();
        }
    }

    @Override
    public void dispose() {
        sourceEditor = null;
        super.dispose();
    }
}
