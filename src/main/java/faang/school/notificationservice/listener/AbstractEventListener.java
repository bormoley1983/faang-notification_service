package faang.school.notificationservice.listener;

import faang.school.notificationservice.model.event.Event;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.model.dto.UserDto;
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

    protected abstract Long getAuthorId(T event);

    public void processEvent(T event) {
        log.info("Processing event: {}", event);
        Long authorId = getAuthorId(event);
        UserDto user = userServiceClient.getUser(authorId);

        String message = messageSource.getMessage(
                getMessageKey(),
                getMessageArgs(event, user),
                null
        );
        log.info("User had message configuration {}", message);
        sendNotification(user, message);
    }

    protected void sendNotification(UserDto userDto, String message) {
        log.info("Attempting to send notification to user: {} with preferred notification means: {}",
                userDto.getId(), userDto.getPreference());

        NotificationService notificationService = notificationServices.stream()
                .filter(service -> service.getPreferredContact() == userDto.getPreference())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("No suitable notificationService found " +
                        "for user with id = %d, preference = %s", userDto.getId(), userDto.getPreference())));

        log.info("Sending notification for userId={}, chatId={}", userDto.getTelegramUsername(),
                userDto.getTelegramChatId());
        notificationService.send(userDto, message);
        log.info("Notification sent to user: {}, id = {} via {}", userDto.getUsername(), userDto.getId(),
                userDto.getPreference());
    }
}
