package org.javacs;

import com.sun.source.util.TreePath;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class Decorations {
    public URI file;
    public List<TreePath> staticFields = new ArrayList<>(),
            instanceFields = new ArrayList<>(),
            mutableVariables = new ArrayList<>(),
            enumConstants = new ArrayList<>();
}
