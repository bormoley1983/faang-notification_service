package faang.school.notificationservice.listener;

import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.event.Event;
import faang.school.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;

import java.util.List;

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

    public void processEvent(T event) {
        log.info("Processing event: {}", event);
        Long recieverId = getRecieverId(event);
        Long senderId = getSenderId(event);
        UserDto reciever = userServiceClient.getUser(recieverId);
        UserDto sender = userServiceClient.getUser(senderId);

        String message = messageSource.getMessage(
                getMessageKey(),
                getMessageArgs(event, sender),
                null
        );
        log.info("User had message configuration {}", message);
        sendNotification(reciever, message);
    }

    protected void sendNotification(UserDto user, String message) {
        log.info("Attempting to send notification to user: {} with preferred notification means: {}",
                user.getId(), user.getPreference());

        NotificationService notificationService = notificationServices.stream()
                .filter(service -> service.getPreferredContact() == user.getPreference())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("No suitable notificationService found " +
                        "for user with id = %d, preference = %s", user.getId(), user.getPreference())));

        log.info("Sending notification for userId={}, chatId={}", user.getTelegramUsername(),
                user.getTelegramChatId());
        notificationService.send(user, message);
        log.info("Notification sent to user: {}, id = {} via {}", user.getUsername(), user.getId(),
                user.getPreference());
    }
}
