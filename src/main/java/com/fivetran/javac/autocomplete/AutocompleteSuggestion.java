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
     * The text which will be inserted into the editor, in place of the prefix
     */
    public String text;

    /**
     * A snippet string. This will allow users to tab through function arguments or other options. e.g.
     * 'myFunction(${1:arg1}, ${2:arg2})'. See the snippets package for more information.
     */
    public String snippet;

    /**
     * The suggestion type. It will be converted into an icon shown against the suggestion. screenshot. Predefined
     * styles exist for variable, constant, property, value, method, function, class, type, keyword, tag, snippet,
     * import, require. This list represents nearly everything being colorized.
     */
    public Type type;

    /**
     * A string that will show in the UI for this suggestion. When not set, snippet || text is displayed. This is useful
     * when snippet or text displays too much, and you want to simplify. e.g. {type: 'attribute', snippet:
     * 'class="$0"$1', displayText: 'class'}
     */
    public Optional<String> displayText = Optional.empty();
    /**
     * The text immediately preceding the cursor, which will be replaced by the text. If not provided, the prefix passed
     * into getSuggestions will be used.
     */
    public Optional<String> replacementPrefix = Optional.empty();
    /**
     * This is shown before the suggestion. Useful for return values. screenshot
     */
    public Optional<String> leftLabel = Optional.empty();
    /**
     * Use this instead of leftLabel if you want to use html for the left label.
     */
    public Optional<String> leftLabelHTML = Optional.empty();
    /**
     * An indicator (e.g. function, variable) denoting the "kind" of suggestion this represents
     */
    public Optional<String> rightLabel = Optional.empty();
    /**
     * Use this instead of rightLabel if you want to use html for the right label.
     */
    public Optional<String> rightLabelHTML = Optional.empty();
    /**
     * Class name for the suggestion in the suggestion list. Allows you to style your suggestion via CSS, if desired
     */
    public Optional<String> className = Optional.empty();
    /**
     * If you want complete control over the icon shown against the suggestion. e.g. iconHTML: '<i
     * class="icon-move-right"></i>' screenshot. The background color of the icon will still be determined (by default)
     * from the type.
     */
    public Optional<String> iconHTML = Optional.empty();
    /**
     * A doc-string summary or short description of the suggestion. When specified, it will be displayed at the bottom
     * of the suggestions list.
     */
    public Optional<String> description = Optional.empty();
    /**
     * A url to the documentation or more information about this suggestion. When specified, a More.. link will be
     * displayed in the description area.
     */
    public Optional<String> descriptionMoreURL = Optional.empty();

    public AutocompleteSuggestion(String text, String snippet, Type type) {
        this.text = text;
        this.snippet = snippet;
        this.type = type;
    }

    public enum Type {
        Variable, Constant, Property, Value, Method, Function, Class, Type, Keyword, Tag, Snippet, Import, Require;

        @JsonValue
        public String toJson() {
            return this.name().toLowerCase();
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
