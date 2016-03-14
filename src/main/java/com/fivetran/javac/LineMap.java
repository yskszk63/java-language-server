package com.fivetran.javac;

import com.fivetran.javac.message.Position;

import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LineMap {
    private final List<Long> startOfLineOffset;

    private LineMap(List<Long> offsets) {
        startOfLineOffset = offsets;
    }

    private static List<Long> findStarts(Reader in) throws IOException {
        List<Long> offsets = new ArrayList<>();
        long offset = 0;

        offsets.add(0L);

        while (true) {
            int next = in.read();

            if (next < 0) {
                // Important for 1-line files, which have no \n chars
                offsets.add(offset);

                return offsets;
            }
            else {
                offset++;

                char nextChar = (char) next;

                if (nextChar == '\n')
                    offsets.add(offset);
            }
        }
    }

    public long offset(int row, int column) {
        return startOfLineOffset.get(row) + column;
    }

    public Position point(long offset) {
        for (int row = 0; row < startOfLineOffset.size() - 1; row++) {
            Long startOffset = startOfLineOffset.get(row);
            Long endOffset = startOfLineOffset.get(row + 1);

            if (endOffset >= offset)
                return new Position(row, offset - startOffset);
        }

        throw new IllegalArgumentException("Offset " + offset + " is after the end of the file " + startOfLineOffset.get(startOfLineOffset.size() - 1));
    }

    public static LineMap fromPath(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return new LineMap(findStarts(new InputStreamReader(in)));
        }
    }

    public static LineMap fromString(String text) throws IOException {
        return new LineMap(findStarts(new StringReader(text)));
    }
}
