package org.javacs.lsp;

import com.google.gson.JsonElement;

public class ResponseError {
    public int code;
    public String message;
    public JsonElement data;

    public ResponseError() {}

    public ResponseError(int code, String message, JsonElement data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
}
