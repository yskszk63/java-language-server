package org.javacs;

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;

import javax.tools.JavaFileObject;

/**
 * Allows use to clear files from Log when we recompile 
 */
public class IncrementalLog extends Log {

    public IncrementalLog(Context context) {
        super(context);

        super.multipleErrors = true;
    }

    public void clear(JavaFileObject source) {
        sourceMap.remove(source);
    }
}
