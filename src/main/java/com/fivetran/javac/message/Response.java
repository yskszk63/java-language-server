package com.fivetran.javac.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fivetran.javac.Main;

import java.util.Optional;
import java.util.OptionalInt;

public class Response {
    public final OptionalInt requestId;
    public Optional<ResponseLint> lint = Optional.empty();
    public Optional<JsonNode> echo = Optional.empty();
    public Optional<ResponseError> error = Optional.empty();
    public Optional<ResponseAutocomplete> autocomplete = Optional.empty();

    public Response(int requestId) {
        this.requestId = OptionalInt.of(requestId);
    }

    public Response() {
        this.requestId = OptionalInt.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Response response = (Response) o;

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
