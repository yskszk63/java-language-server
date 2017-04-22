package org.javacs;

import java.util.ArrayList;
import java.util.List;

public class JavaSettings {
    public Java java = new Java();

    public static class Java {
        public List<String> externalDependencies = new ArrayList<>();
    }
}