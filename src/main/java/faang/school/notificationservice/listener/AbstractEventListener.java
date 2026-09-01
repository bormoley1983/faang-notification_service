package faang.school.notificationservice.listener;

import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.exception.NotificationDeliveryException;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import faang.school.notificationservice.model.event.Event;
import faang.school.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractEventListener<T extends Event> {
    private final UserServiceClient userServiceClient;
    private final List<NotificationService> notificationServices;
    private final MessageSource messageSource;

    protected abstract String getMessageKey();

    protected abstract Object[] getMessageArgs(T event, UserDto user);
    protected abstract Long getRecieverId(T event);
    protected abstract Long getSenderId(T event);

    /**
     * Returns the recipient ids this event must be delivered to. Single-recipient
     * events return a one-element list; fan-out events (e.g. event start) return all
     * participants so users can be fetched in one bulk call (NOT-06).
     */
    protected List<Long> getRecipientIds(T event) {
        return List.of(getRecieverId(event));
    }

    /**
     * Returns the locale to use for message resolution, or null for the default locale.
     */
    protected Locale getMessageLocale(T event) {
        return null;
    }

    public void processEvent(T event) {
        log.info("Processing event of type {}", event.getClass().getSimpleName());
        Long senderId = getSenderId(event);
        List<Long> recipientIds = getRecipientIds(event).stream()
                .filter(id -> id != null && !id.equals(senderId))
                .distinct()
                .toList();
        if (recipientIds.isEmpty()) {
            log.info("No recipients to notify for event of type {}", event.getClass().getSimpleName());
            return;
        }

        // NOT-06: one bulk fetch instead of two calls per recipient. The user service's
        // /users/list endpoint accepts a list of users and only uses their ids.
        List<UserDto> idOnlyUsers = recipientIds.stream()
                .map(id -> {
                    UserDto dto = new UserDto();
                    dto.setId(id);
                    return dto;
                })
                .toList();

        Map<Long, UserDto> usersById;
        try {
            usersById = userServiceClient.getUsersByIds(idOnlyUsers)
                    .stream()
                    .collect(Collectors.toMap(UserDto::getId, Function.identity(), (a, b) -> a));
        } catch (RuntimeException e) {
            log.error("Failed to bulk-fetch recipients for event of type {}; recipientCount = {}",
                    event.getClass().getSimpleName(), recipientIds.size(), e);
            throw new NotificationDeliveryException(
                    "Failed to fetch recipients for event of type "
                            + event.getClass().getSimpleName()
                            + ", recipientCount = " + recipientIds.size(), e);
        }

        Locale locale = getMessageLocale(event);
        String message = messageSource.getMessage(getMessageKey(), getMessageArgs(event, null), locale);

        // NOT-06: isolate per-recipient failures so one bad recipient does not cause the
        // whole Kafka record to be retried (and already-delivered messages duplicated).
        for (Long recipientId : recipientIds) {
            UserDto recipient = usersById.get(recipientId);
            if (recipient == null) {
                log.warn("Recipient user id = {} not found, skipping notification",
                        recipientId);
                continue;
            }
            try {
                sendNotification(recipient, message);
            } catch (RuntimeException e) {
                log.error("Failed to deliver notification to user id = {} via {}; " +
                        "continuing with remaining recipients", recipient.getId(),
                        recipient.getPreference(), e);
            }
        }
    }

    protected void sendNotification(UserDto user, String message) {
        PreferredContact preference = user.getPreference();
        log.info("Attempting to send notification to user id = {} with preferred contact = {}",
                user.getId(), preference);

        // NOT-07: unsupported or absent preference falls back to EMAIL instead of
        // failing the whole record as a poison message.
        NotificationService notificationService = notificationServices.stream()
                .filter(service -> service.getPreferredContact() == preference)
                .findFirst()
                .orElseGet(() -> notificationServices.stream()
                        .filter(service -> service.getPreferredContact() == PreferredContact.EMAIL)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No suitable notificationService found for user with id = " +
                                        user.getId() + ", preference = " + preference)));

        notificationService.send(user, message);
        log.info("Notification sent to user id = {} via {}", user.getId(),
                notificationService.getPreferredContact());
    }
}
