package com.agentcrawler.core;

public class AppException extends RuntimeException {
    private final ErrorCode code;

    public AppException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AppException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
