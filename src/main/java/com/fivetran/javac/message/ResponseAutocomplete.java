package com.fivetran.javac.message;

import java.util.Set;

public class ResponseAutocomplete {
    public final Set<AutocompleteSuggestion> suggestions;

    public ResponseAutocomplete(Set<AutocompleteSuggestion> suggestions) {
        this.suggestions = suggestions;
    }
}
