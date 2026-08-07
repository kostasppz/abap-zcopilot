package com.abapguardian.eclipse.views;

import com.abapguardian.eclipse.adapter.AdtEditorAdapter;
import com.abapguardian.eclipse.api.GuardianAnalysisResult;
import com.abapguardian.eclipse.api.GuardianFinding;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.SWT;
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
                        ? f.getSuggestedCode() : f.getRecommendation());

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
        manager.add(new Action("Review Suggested Fix…") {
            @Override
            public void run() {
                GuardianFinding finding = selectedFinding();
                IEditorPart editor = activeEditor();
                if (finding != null && editor != null) {
                    SuggestedFixDialog.proposeFix(getSite().getShell(), editor, finding);
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

    private IEditorPart activeEditor() {
        IWorkbenchPage page = getSite().getWorkbenchWindow().getActivePage();
        return page == null ? null : page.getActiveEditor();
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
        IEditorPart editor = page.getActiveEditor();
        if (editor != null) {
            AdtEditorAdapter.revealLine(editor, finding.getStartLine());
        }
    }

    @Override
    public void setFocus() {
        if (viewer != null) {
            viewer.getControl().setFocus();
        }
    }
}
