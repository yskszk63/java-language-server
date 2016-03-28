package org.javacs.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.javacs.Main;

import java.util.Optional;

public class Request {
    public int requestId;

    /**
     * Handy to test if the channel is working
     */
    public Optional<JsonNode> echo = Optional.empty();

    public Optional<RequestLint> lint = Optional.empty();

    public Optional<RequestAutocomplete> autocomplete = Optional.empty();

    @JsonProperty("goto")
    public Optional<RequestGoto> requestGoto = Optional.empty();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Request response = (Request) o;

        try {
            return Main.JSON.writeValueAsString(this).equals(Main.JSON.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        try {
            return Main.JSON.writeValueAsString(this).hashCode();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
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
