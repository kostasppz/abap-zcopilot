package com.abapguardian.eclipse.lifecycle;

import com.abapguardian.eclipse.adapter.AdtEditorAdapter;
import com.abapguardian.eclipse.handlers.AnalyzeCurrentHandler;
import com.abapguardian.eclipse.jobs.AnalyzeJob;
import com.abapguardian.eclipse.preferences.GuardianPreferences;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;

import java.util.Map;
import java.util.WeakHashMap;

/** Debounced, cancellable live analysis for active text editors. */
public final class LiveAnalysisController implements IPartListener2 {

    private static final Map<IWorkbenchWindow, LiveAnalysisController> CONTROLLERS =
            new WeakHashMap<>();

    private final IWorkbenchWindow window;
    private IEditorPart editor;
    private IDocument document;
    private AnalyzeJob pending;
    private long generation;

    private final IDocumentListener documentListener = new IDocumentListener() {
        @Override
        public void documentAboutToBeChanged(DocumentEvent event) {
            // No action before the document contains the new text.
        }

        @Override
        public void documentChanged(DocumentEvent event) {
            if (GuardianPreferences.isLiveAnalysisEnabled()) {
                schedule(false);
            }
        }
    };

    private final IPropertyListener editorPropertyListener = (source, propertyId) -> {
        if (propertyId == IEditorPart.PROP_DIRTY && editor != null && !editor.isDirty()
                && GuardianPreferences.isAnalyzeOnSave()) {
            schedule(true);
        }
    };

    private LiveAnalysisController(IWorkbenchWindow window) {
        this.window = window;
    }

    public static synchronized void install(IWorkbenchWindow window) {
        if (window == null || CONTROLLERS.containsKey(window)) {
            return;
        }
        LiveAnalysisController controller = new LiveAnalysisController(window);
        CONTROLLERS.put(window, controller);
        IWorkbenchPage page = window.getActivePage();
        if (page != null) {
            page.addPartListener(controller);
            controller.attach(page.getActiveEditor());
        }
    }

    private void attach(IEditorPart newEditor) {
        if (newEditor == editor) {
            return;
        }
        detach();
        editor = newEditor;
        if (editor == null) {
            return;
        }
        document = AdtEditorAdapter.getDocument(editor).orElse(null);
        if (document != null) {
            document.addDocumentListener(documentListener);
            editor.addPropertyListener(editorPropertyListener);
        }
    }

    private void detach() {
        generation++;
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
        if (document != null) {
            document.removeDocumentListener(documentListener);
        }
        if (editor != null) {
            editor.removePropertyListener(editorPropertyListener);
        }
        editor = null;
        document = null;
    }

    private void schedule(boolean fromSave) {
        if (editor == null || document == null) {
            return;
        }
        if (pending != null) {
            pending.cancel();
        }
        long token = ++generation;
        IEditorPart analyzedEditor = editor;
        String source = document.get();
        String objectName = AdtEditorAdapter.getObjectName(analyzedEditor);
        String objectType = AdtEditorAdapter.getObjectType(analyzedEditor);
        pending = new AnalyzeJob(source, objectName, objectType,
                GuardianPreferences.isLiveUseAi(), false,
                result -> Display.getDefault().asyncExec(() -> {
                    if (token == generation && analyzedEditor == editor) {
                        AnalyzeCurrentHandler.showInFindingsView(
                                window, analyzedEditor, result, false);
                    }
                }), message -> { });
        pending.schedule(fromSave ? 0 : GuardianPreferences.getLiveDelayMs());
    }

    @Override
    public void partActivated(IWorkbenchPartReference partRef) {
        if (partRef.getPart(false) instanceof IEditorPart activeEditor) {
            attach(activeEditor);
        }
    }

    @Override
    public void partClosed(IWorkbenchPartReference partRef) {
        if (partRef.getPart(false) == editor) {
            detach();
        }
    }

    @Override public void partBroughtToTop(IWorkbenchPartReference partRef) { }
    @Override public void partDeactivated(IWorkbenchPartReference partRef) { }
    @Override public void partOpened(IWorkbenchPartReference partRef) { }
    @Override public void partHidden(IWorkbenchPartReference partRef) { }
    @Override public void partVisible(IWorkbenchPartReference partRef) { }
    @Override public void partInputChanged(IWorkbenchPartReference partRef) { }
}
