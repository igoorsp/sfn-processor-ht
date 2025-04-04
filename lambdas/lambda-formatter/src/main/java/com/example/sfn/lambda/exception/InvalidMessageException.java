package com.example.sfn.lambda.exception;

public class InvalidMessageException extends MessageProcessingException {
    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Exception e) {
        super(message, e);
    }
}