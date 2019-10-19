package org.javacs;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.javacs.lsp.*;

class CachedLint {
    final Path file;
    String contents;
    // TODO save CompilationUnitTree root instead of Parser parse, so we can avoid redundant parse
    Parser parse;
    List<DiagnosticHolder> errors = new ArrayList<>();
    List<Span> statics = new ArrayList<>(), fields = new ArrayList<>();

    CachedLint(Path file) {
        this.file = file;
        this.contents = "";
        this.parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.EPOCH));
    }

    Span edited() {
        var newContents = FileStore.contents(file);
        if (contents.equals(newContents)) {
            return Span.EMPTY;
        }
        var prefix = commonPrefix(contents, newContents);
        var suffix = commonSuffix(contents, newContents);
        if (prefix + suffix == 0) {
            return Span.INVALID;
        }
        return parse.enclosingMethod(prefix, contents.length() - suffix);
    }

    private int commonPrefix(String x, String y) {
        int i = 0;
        while (i < x.length() && i < y.length()) {
            if (x.charAt(i) != y.charAt(i)) {
                return i;
            }
            i++;
        }
        return i;
    }

    private int commonSuffix(String x, String y) {
        int i = 0;
        while (i < x.length() && i < y.length()) {
            if (x.charAt(x.length() - i - 1) != y.charAt(y.length() - i - 1)) {
                return i;
            }
            i++;
        }
        return i;
    }

    String pruneNewContents(Span editedInOldContents) {
        var newContents = FileStore.contents(file);
        if (editedInOldContents == Span.INVALID) {
            return newContents;
        }
        var oldContents = parse.prune(editedInOldContents);
        var prefix = oldContents.substring(0, editedInOldContents.start);
        var suffix = oldContents.substring(editedInOldContents.until);
        var shift = newContents.length() - oldContents.length();
        var between = newContents.substring(editedInOldContents.start, editedInOldContents.until + shift);
        return prefix + between + suffix;
    }

    void update(Span edited, List<DiagnosticHolder> newErrors, ColorsHolder newColors) {
        var newContents = FileStore.contents(file);
        if (edited == Span.INVALID) {
            contents = newContents;
            parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.now()));
            errors = newErrors;
            fields = newColors.fields;
            statics = newColors.statics;
            return;
        }
        var shift = newContents.length() - contents.length();
        // Update diagnostics
        var oldErrors = shiftOldDiagnostics(errors, edited, shift);
        newErrors = keepNewDiagnosticsInEditedRegion(newErrors, edited, shift);
        errors.clear();
        errors.addAll(oldErrors);
        errors.addAll(newErrors);
        // Update fields
        var oldFields = shiftOldSpans(fields, edited, shift);
        var newFields = keepNewSpansInEditedRegion(newColors.fields, edited, shift);
        fields.clear();
        fields.addAll(oldFields);
        fields.addAll(newFields);
        // Update statics
        var oldStatics = shiftOldSpans(statics, edited, shift);
        var newStatics = keepNewSpansInEditedRegion(newColors.statics, edited, shift);
        statics.clear();
        statics.addAll(oldStatics);
        statics.addAll(newStatics);
        // Re-parse
        contents = newContents;
        parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.now()));
    }

    PublishDiagnosticsParams lspDiagnostics() {
        var lines = parse.root.getLineMap();
        var result = new PublishDiagnosticsParams();
        result.uri = file.toUri();
        for (var e : errors) {
            result.diagnostics.add(e.lspDiagnostic(lines));
        }
        return result;
    }

    SemanticColors lspColors() {
        var lines = parse.root.getLineMap();
        var result = new SemanticColors();
        result.uri = file.toUri();
        for (var span : statics) {
            var range = span.asRange(lines);
            result.statics.add(range);
        }
        for (var span : fields) {
            var range = span.asRange(lines);
            result.fields.add(range);
        }
        return result;
    }

    private List<DiagnosticHolder> shiftOldDiagnostics(List<DiagnosticHolder> old, Span edited, int shift) {
        var shifted = new ArrayList<DiagnosticHolder>();
        for (var d : old) {
            if (d.end <= edited.start) {
                shifted.add(d);
            } else if (edited.until <= d.start) {
                d.shift(shift);
                shifted.add(d);
            }
        }
        return shifted;
    }

    private List<DiagnosticHolder> keepNewDiagnosticsInEditedRegion(
            List<DiagnosticHolder> updated, Span edited, int shift) {
        if (edited == Span.INVALID) {
            return updated;
        }
        var keep = new ArrayList<DiagnosticHolder>();
        var newStart = edited.start;
        var newUntil = edited.until + shift;
        for (var d : updated) {
            if (newStart <= d.start && d.end <= newUntil) {
                keep.add(d);
            }
        }
        return keep;
    }

    private List<Span> shiftOldSpans(List<Span> old, Span edited, int shift) {
        var shifted = new ArrayList<Span>();
        for (var s : old) {
            if (s.until <= edited.start) {
                shifted.add(s);
            } else if (edited.until <= s.start) {
                s = new Span(s.start + shift, s.until + shift);
                shifted.add(s);
            }
        }
        return shifted;
    }

    private List<Span> keepNewSpansInEditedRegion(List<Span> updated, Span edited, int shift) {
        var keep = new ArrayList<Span>();
        var newStart = edited.start;
        var newUntil = edited.until + shift;
        for (var s : updated) {
            if (newStart <= s.start && s.until <= newUntil) {
                keep.add(s);
            }
        }
        return keep;
    }
}
