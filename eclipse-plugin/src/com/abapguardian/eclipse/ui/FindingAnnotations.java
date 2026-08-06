package com.abapguardian.eclipse.ui;

import com.abapguardian.eclipse.api.GuardianFinding;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Adds/removes Guardian annotations in an editor's annotation model. */
public final class FindingAnnotations {

    public static final String TYPE = "com.abapguardian.eclipse.annotation.finding";

    private FindingAnnotations() {
    }

    /** Removes previous Guardian annotations, then adds one per finding. */
    public static void apply(IAnnotationModel model, IDocument document,
                             List<GuardianFinding> findings) {
        if (model == null || document == null) {
            return;
        }
        clear(model);
        for (GuardianFinding finding : findings) {
            try {
                int startOffset = document.getLineOffset(finding.getStartLine() - 1)
                        + Math.max(0, finding.getStartColumn() - 1);
                int endLine = Math.min(finding.getEndLine() - 1, document.getNumberOfLines() - 1);
                int endOffset = document.getLineOffset(endLine)
                        + Math.max(0, finding.getEndColumn());
                int length = Math.max(1, endOffset - startOffset);
                Annotation annotation = new Annotation(TYPE, false,
                        "[" + finding.getSeverity() + "] " + finding.getRuleId()
                                + ": " + finding.getTitle());
                model.addAnnotation(annotation, new Position(startOffset, length));
            } catch (BadLocationException e) {
                // Position no longer valid — skip; never guess.
            }
        }
    }

    /** Removes all Guardian annotations from the model. */
    public static void clear(IAnnotationModel model) {
        List<Annotation> stale = new ArrayList<>();
        Iterator<Annotation> it = model.getAnnotationIterator();
        while (it.hasNext()) {
            Annotation a = it.next();
            if (TYPE.equals(a.getType())) {
                stale.add(a);
            }
        }
        stale.forEach(model::removeAnnotation);
    }
}
