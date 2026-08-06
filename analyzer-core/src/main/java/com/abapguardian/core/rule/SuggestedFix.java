package com.abapguardian.core.rule;

import java.util.Optional;

/**
 * A suggested fix for a finding. Deterministic fixes may provide a concrete
 * {@link TextEdit}; heuristic or AI-provided suggestions return an empty edit
 * and only describe the change.
 */
public interface SuggestedFix {

    String getDescription();

    Optional<TextEdit> createEdit();

    static SuggestedFix descriptionOnly(String description) {
        return new SuggestedFix() {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public Optional<TextEdit> createEdit() {
                return Optional.empty();
            }
        };
    }

    static SuggestedFix withEdit(String description, TextEdit edit) {
        return new SuggestedFix() {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public Optional<TextEdit> createEdit() {
                return Optional.of(edit);
            }
        };
    }
}
