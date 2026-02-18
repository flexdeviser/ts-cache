package org.e4s.client.http;

/**
 * Exception thrown when an e4s client operation fails.
 * 
 * <p>This exception wraps various failure scenarios:
 * <ul>
 *   <li>HTTP connection failures</li>
 *   <li>Non-successful HTTP responses (4xx, 5xx)</li>
 *   <li>JSON serialization/deserialization errors</li>
 *   <li>Network timeouts</li>
 * </ul>
 */
public class E4sClientException extends RuntimeException {

    public E4sClientException(String message) {
        super(message);
    }

    public E4sClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
