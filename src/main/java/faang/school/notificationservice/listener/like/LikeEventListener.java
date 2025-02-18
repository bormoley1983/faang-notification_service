package faang.school.notificationservice.listener.like;

import faang.school.event.NotificationLikeEvent;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.dto.UserDto;
import faang.school.notificationservice.listener.EventListener;
import faang.school.notificationservice.service.NotificationServiceHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeEventListener implements EventListener<NotificationLikeEvent> {

    private final NotificationServiceHandler notificationServiceHandler;
    private final UserServiceClient userServiceClient;
    private final MessageSource messageSource;

    @Override
    @KafkaListener(topics = "${spring.kafka.topics.like-topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void listenEvent(NotificationLikeEvent event) {
        log.info("Received LikeEvent: {}", event);

        UserDto user = userServiceClient.getUser(event.getAuthorId());

        String message = messageSource.getMessage(
                "like.notification",
                new Object[]{event.getUserId(), event.getPostId()},
                null
        );

        notificationServiceHandler.sendNotification(user, message);
    }
}