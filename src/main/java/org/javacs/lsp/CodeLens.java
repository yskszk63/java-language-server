package org.javacs.lsp;

import com.google.gson.JsonArray;

public class CodeLens {
    public Range range;
    public Command command;
    public JsonArray data;
}
