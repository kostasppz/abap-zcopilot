package com.abapguardian.eclipse.adapter;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import java.util.Optional;

/**
 * Adapter isolating everything ADT-specific. The rest of the plug-in only
 * talks to this class, never to ADT types directly.
 *
 * <p>Uses only public platform interfaces ({@link ITextEditor},
 * {@link IDocument}). ADT editors implement {@link ITextEditor}, so source
 * extraction works generically; if SAP publishes stable public ADT APIs for
 * richer integration (object type detection, activation state, etc.) that
 * logic belongs here, and nowhere else.
 */
public final class AdtEditorAdapter {

    private AdtEditorAdapter() {
    }

    /** Extracts the document text from any text-based editor (incl. ADT). */
    public static Optional<String> getSource(IEditorPart editor) {
        ITextEditor textEditor = toTextEditor(editor);
        if (textEditor == null) {
            return Optional.empty();
        }
        IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        return document == null ? Optional.empty() : Optional.of(document.get());
    }

    /** Returns the document backing the editor, if any. */
    public static Optional<IDocument> getDocument(IEditorPart editor) {
        ITextEditor textEditor = toTextEditor(editor);
        if (textEditor == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput()));
    }

    /** Returns the editor annotation model used for ruler and underline markers. */
    public static Optional<IAnnotationModel> getAnnotationModel(IEditorPart editor) {
        ITextEditor textEditor = toTextEditor(editor);
        if (textEditor == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                textEditor.getDocumentProvider().getAnnotationModel(textEditor.getEditorInput()));
    }

    /** Returns selected text, or an empty string when no text is selected. */
    public static String getSelectedText(IEditorPart editor) {
        ITextEditor textEditor = toTextEditor(editor);
        if (textEditor == null || textEditor.getSelectionProvider() == null) {
            return "";
        }
        if (textEditor.getSelectionProvider().getSelection() instanceof ITextSelection selection) {
            return selection.getText() == null ? "" : selection.getText();
        }
        return "";
    }

    /** One-based line where the active text selection starts. */
    public static int getSelectionStartLine(IEditorPart editor) {
        ITextEditor textEditor = toTextEditor(editor);
        if (textEditor != null && textEditor.getSelectionProvider() != null
                && textEditor.getSelectionProvider().getSelection() instanceof ITextSelection selection) {
            return selection.getStartLine() + 1;
        }
        return 1;
    }

    public static int getSelectionOffset(IEditorPart editor) {
        ITextEditor textEditor = toTextEditor(editor);
        if (textEditor != null && textEditor.getSelectionProvider() != null
                && textEditor.getSelectionProvider().getSelection() instanceof ITextSelection selection) {
            return selection.getOffset();
        }
        return -1;
    }

    public static int getSelectionLength(IEditorPart editor) {
        ITextEditor textEditor = toTextEditor(editor);
        if (textEditor != null && textEditor.getSelectionProvider() != null
                && textEditor.getSelectionProvider().getSelection() instanceof ITextSelection selection) {
            return selection.getLength();
        }
        return 0;
    }

    /**
     * Returns a bounded source excerpt around a one-based finding range.
     * The excerpt is used only for an explicit suggested-fix request and is
     * never persisted by the plug-in.
     */
    public static String getSourceSnippet(IEditorPart editor, int startLine, int endLine,
                                          int contextLines, int maxCharacters) {
        IDocument document = getDocument(editor).orElse(null);
        if (document == null || document.getNumberOfLines() == 0 || maxCharacters <= 0) {
            return "";
        }
        int first = Math.max(0, startLine - 1 - Math.max(0, contextLines));
        int last = Math.min(document.getNumberOfLines() - 1,
                Math.max(first, endLine - 1 + Math.max(0, contextLines)));
        try {
            int offset = document.getLineOffset(first);
            int endOffset = document.getLineOffset(last) + document.getLineLength(last);
            String snippet = document.get(offset, endOffset - offset);
            return snippet.length() <= maxCharacters
                    ? snippet : snippet.substring(0, maxCharacters);
        } catch (BadLocationException exception) {
            return "";
        }
    }

    /** Best-effort ABAP object name from the editor input. */
    public static String getObjectName(IEditorPart editor) {
        IEditorInput input = editor.getEditorInput();
        String name = input != null ? input.getName() : null;
        return name == null || name.isBlank() ? "UNKNOWN" : name;
    }

    /**
     * Heuristic object type from the editor input name. ADT object types
     * cannot be determined reliably through public APIs alone.
     */
    public static String getObjectType(IEditorPart editor) {
        String name = getObjectName(editor).toUpperCase();
        if (name.contains(".ACLASS") || name.startsWith("ZCL_") || name.startsWith("CL_")) {
            return "CLAS";
        }
        if (name.startsWith("ZIF_") || name.startsWith("IF_")) {
            return "INTF";
        }
        return "PROG";
    }

    /** Reveals a 1-based line in the editor, if it is a text editor. */
    public static void revealLine(IEditorPart editor, int line) {
        ITextEditor textEditor = toTextEditor(editor);
        if (textEditor == null) {
            return;
        }
        IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        if (document == null) {
            return;
        }
        try {
            int offset = document.getLineOffset(Math.max(0, line - 1));
            int length = document.getLineLength(Math.max(0, line - 1));
            textEditor.selectAndReveal(offset, Math.max(0, length - 1));
        } catch (org.eclipse.jface.text.BadLocationException e) {
            // Line no longer exists — ignore; never guess a different position.
        }
    }

    private static ITextEditor toTextEditor(IEditorPart editor) {
        if (editor instanceof ITextEditor textEditor) {
            return textEditor;
        }
        return editor != null ? editor.getAdapter(ITextEditor.class) : null;
    }
}
