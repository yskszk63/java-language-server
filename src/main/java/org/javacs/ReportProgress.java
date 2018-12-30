package org.javacs;

public interface ReportProgress {
    void start(String message);

    void progress(String message, int n, int total);

    public static final ReportProgress EMPTY =
            new ReportProgress() {
                @Override
                public void start(String message) {}

                @Override
                public void progress(String message, int n, int total) {}
            };
}
