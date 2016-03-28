package org.javacs;

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;

import javax.tools.JavaFileObject;

public class IncrementalLog extends Log {

    public IncrementalLog(Context context) {
        super(context);
    }

    public void clear(JavaFileObject source) {
        sourceMap.remove(source);
    }
}
