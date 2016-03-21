package com.fivetran.javac;

import org.junit.BeforeClass;

import java.io.IOException;

public class Fixtures {
    @BeforeClass
    public static void setup() throws IOException {
        LoggingFormat.startLogging();
    }
}
