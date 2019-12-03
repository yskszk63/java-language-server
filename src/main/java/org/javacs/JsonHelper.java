package org.javacs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonHelper {
    public static final Gson GSON = new GsonBuilder().registerTypeAdapter(Ptr.class, new PtrAdapter()).create();
}
