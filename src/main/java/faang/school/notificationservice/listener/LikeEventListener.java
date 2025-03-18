package faang.school.notificationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.event.NotificationLikeEvent;
import faang.school.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
        return new Object[]{event.getUserId(), event.getPostId()};
    }

    @Override
    protected Long getRecieverId(NotificationLikeEvent event) {
        return event.getAuthorId();
    }

    @Override
    protected Long getSenderId(NotificationLikeEvent event) {
        return event.getUserId();
    }

    @KafkaListener(topics = "${spring.kafka.topics.like-topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String event) {
        try {
            NotificationLikeEvent likeEvent = objectMapper.readValue(event, NotificationLikeEvent.class);
            log.info("Parsed event: {}", event);
            processEvent(likeEvent);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON: {}", event);
            throw new RuntimeException(e);
        }
    }
}
