package com.abapguardian.eclipse.ui;

import com.abapguardian.eclipse.adapter.AdtEditorAdapter;
import com.abapguardian.eclipse.api.GuardianFinding;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shows a suggested fix as a side-by-side compare and applies it only after
 * explicit confirmation. The edit is applied as ONE document replace so the
 * user can undo it with a single Ctrl+Z. The document is never saved and the
 * object is never activated by the plug-in.
 */
public final class SuggestedFixDialog {

    private SuggestedFixDialog() {
    }

    /**
     * Presents the compare dialog; on confirmation replaces the finding's
     * line range with the suggested code.
     *
     * @return true when the edit was applied.
     */
    public static boolean proposeFix(Shell shell, IEditorPart editor, GuardianFinding finding) {
        if (finding.getSuggestedCode() == null || finding.getSuggestedCode().isBlank()) {
            MessageDialog.openInformation(shell, "ABAP Guardian",
                    "This finding has no suggested code.");
            return false;
        }
        IDocument document = AdtEditorAdapter.getDocument(editor).orElse(null);
        if (document == null) {
            MessageDialog.openError(shell, "ABAP Guardian",
                    "The active editor does not expose an editable document.");
            return false;
        }
        String original;
        int offset;
        int length;
        try {
            int startOffset = document.getLineOffset(finding.getStartLine() - 1);
            int endLine = Math.min(finding.getEndLine() - 1, document.getNumberOfLines() - 1);
            int endOffset = document.getLineOffset(endLine) + document.getLineLength(endLine);
            offset = startOffset;
            length = endOffset - startOffset;
            original = document.get(offset, length);
        } catch (BadLocationException e) {
            MessageDialog.openError(shell, "ABAP Guardian",
                    "The document changed since the analysis; re-run the analysis first.");
            return false;
        }

        CompareConfiguration config = new CompareConfiguration();
        config.setLeftLabel("Current code");
        config.setRightLabel("Suggested code (" + finding.getRuleId() + ")");
        config.setLeftEditable(false);
        config.setRightEditable(false);
        CompareEditorInput input = new CompareEditorInput(config) {
            @Override
            protected Object prepareInput(IProgressMonitor monitor) {
                return new DiffNode(new TextElement("current.abap", original),
                        new TextElement("suggested.abap", finding.getSuggestedCode()));
            }
        };
        input.setTitle("ABAP Guardian — Review Suggested Fix");
        try {
            input.run(null);
        } catch (Exception e) {
            com.abapguardian.eclipse.Activator.logError("Compare preparation failed", e);
            return false;
        }
        org.eclipse.compare.CompareUI.openCompareDialog(input);

        boolean confirmed = MessageDialog.openConfirm(shell, "ABAP Guardian",
                "Apply the suggested fix for " + finding.getRuleId() + "?\n\n"
                        + "The change is applied in the editor only — nothing is saved or "
                        + "activated. You can undo it with a single Undo.");
        if (!confirmed) {
            return false;
        }
        try {
            // Single replace => single undo step.
            document.replace(offset, length, finding.getSuggestedCode());
            return true;
        } catch (BadLocationException e) {
            MessageDialog.openError(shell, "ABAP Guardian",
                    "The document changed; the fix was not applied.");
            return false;
        }
    }

    private static final class TextElement implements ITypedElement, IStreamContentAccessor {
        private final String name;
        private final String content;

        TextElement(String name, String content) {
            this.name = name;
            this.content = content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Image getImage() {
            return null;
        }

        @Override
        public String getType() {
            return "abap";
        }

        @Override
        public InputStream getContents() {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
