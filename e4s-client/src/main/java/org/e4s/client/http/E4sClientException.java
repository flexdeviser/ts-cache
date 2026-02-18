package org.e4s.client.http;

public class E4sClientException extends RuntimeException {

    public E4sClientException(String message) {
        super(message);
    }

    public E4sClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
