package faang.school.notificationservice.exception;

/**
 * Thrown when a Kafka event payload cannot be deserialized from JSON.
 */
public class EventDeserializationException extends RuntimeException {

    public EventDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
