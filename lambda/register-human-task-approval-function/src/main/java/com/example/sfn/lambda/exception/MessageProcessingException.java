package com.example.sfn.lambda.exception;

public class MessageProcessingException extends RuntimeException {
    public MessageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageProcessingException(String message) {
        super(message);
    }
}