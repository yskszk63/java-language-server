package com.fivetran.javac;

import com.fivetran.javac.logging.LoggingFormat;
import org.junit.BeforeClass;

import java.io.IOException;

public class Fixtures {
    @BeforeClass
    public static void setup() throws IOException {
        LoggingFormat.startLogging();
    }
}
