package faang.school.notificationservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.notificationservice.model.event.NotificationLikeEvent;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class LikeEventListener extends AbstractEventListener<NotificationLikeEvent> {
    private final ObjectMapper objectMapper;

    public LikeEventListener(UserServiceClient userServiceClient,
                             List<NotificationService> notificationServices,
                             MessageSource messageSource, ObjectMapper objectMapper) {
        super(userServiceClient, notificationServices, messageSource);
        this.objectMapper = objectMapper;
    }

    @Override
    protected String getMessageKey() {
        return "like.notification";
    }

    @Override
    protected Object[] getMessageArgs(NotificationLikeEvent event, UserDto user) {
        return new Object[]{user.getUsername(), event.getPostId()};
    }

    @Override
    protected Long getAuthorId(NotificationLikeEvent event) {
        return event.getAuthorId();
    }

    @KafkaListener(topics = "${spring.kafka.topics.like-topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String jsonEvent) {
        try {
            NotificationLikeEvent event = objectMapper.readValue(jsonEvent, NotificationLikeEvent.class);
            log.info("Parsed event: {}", event);
            processEvent(event);
        } catch (IOException e) {
            log.error("Error parsing JSON", e);
        }
    }
}
