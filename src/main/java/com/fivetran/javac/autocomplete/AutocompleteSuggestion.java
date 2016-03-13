package com.fivetran.javac.autocomplete;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fivetran.javac.Main;

import java.util.Optional;

/**
 * Jackson JSON definition for Atom autocomplete suggestion
 * <p>
 * {@see https://github.com/atom/autocomplete-plus/wiki/Provider-API#suggestions}
 */
public class AutocompleteSuggestion {

    /**
     * The label of this completion item. By default
     * this is also the text that is inserted when selecting
     * this completion.
     */
    public final String label;

    /**
     * A string that should be inserted in a document when selecting
     * this completion. When `falsy` the [label](#CompletionItem.label)
     * is used.
     */
    public final String insertText;

    /**
     * The kind of this completion item. Based on the kind
     * an icon is chosen by the editor.
     */
    public final Type kind;

    /**
     * A human-readable string with additional information
     * about this item, like type or symbol information.
     */
    public Optional<String> detail;

    /**
     * A human-readable string that represents a doc-comment.
     */
    public Optional<String> documentation;

    /**
     * A string that should be used when comparing this item
     * with other items. When `falsy` the [label](#CompletionItem.label)
     * is used.
     */
    public Optional<String> sortText;

    /**
     * A string that should be used when filtering a set of
     * completion items. When `falsy` the [label](#CompletionItem.label)
     * is used.
     */
    public Optional<String> filterText;

    public AutocompleteSuggestion(String label, String insertText, Type kind) {
        this.label = label;
        this.insertText = insertText;
        this.kind = kind;
    }

    /**
     * Must exactly match vscode.CompletionItemKind 
     */
    public enum Type {
		Text,
		Method,
		Function,
		Constructor,
		Field,
		Variable,
		Class,
		Interface,
		Module,
		Property,
		Unit,
		Value,
		Enum,
		Keyword,
		Snippet,
		Color,
		File,
		Reference;
        
        @JsonValue
        public int toJson() {
            return this.ordinal();
        }
    }

    @Override
    public String toString() {
        try {
            return Main.JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
