package com.fivetran.javac.message;

import com.fivetran.javac.autocomplete.AutocompleteSuggestion;

import java.util.Set;

public class ResponseAutocomplete {
    public final Set<AutocompleteSuggestion> suggestions;

    public ResponseAutocomplete(Set<AutocompleteSuggestion> suggestions) {
        this.suggestions = suggestions;
    }
}
