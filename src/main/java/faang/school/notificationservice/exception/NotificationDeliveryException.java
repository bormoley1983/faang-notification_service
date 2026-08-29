package faang.school.notificationservice.exception;

/**
 * Thrown when a notification channel fails to deliver a message. Propagating this
 * exception from a Kafka listener prevents the consumer offset from being committed
 * for an undelivered notification, so the record is retried / routed to the DLQ.
 */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message) {
        super(message);
    }

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
