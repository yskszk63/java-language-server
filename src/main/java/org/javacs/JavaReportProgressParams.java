package org.javacs;

public class JavaReportProgressParams {
    private String message;

    public JavaReportProgressParams() {}

    public JavaReportProgressParams(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
