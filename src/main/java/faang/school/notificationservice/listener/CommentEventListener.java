package faang.school.notificationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.event.NotificationCommentEvent;
import faang.school.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class CommentEventListener extends AbstractEventListener<NotificationCommentEvent> {
    private final ObjectMapper objectMapper;

    public CommentEventListener(UserServiceClient userServiceClient,
                                List<NotificationService> notificationServices,
                                MessageSource messageSource, ObjectMapper objectMapper) {
        super(userServiceClient, notificationServices, messageSource);
        this.objectMapper = objectMapper;
    }

    @Override
    protected String getMessageKey() {
        return "comment.notification";
    }

    @Override
    protected Object[] getMessageArgs(NotificationCommentEvent event, UserDto user) {
        return new Object[] {user.getUsername(), event.getContent()};
    }

    @Override
    protected Long getRecieverId(NotificationCommentEvent event) {
        return event.getPostAuthorId();
    }

    @Override
    protected Long getSenderId(NotificationCommentEvent event) {
        return event.getAuthorId();
    }

    @KafkaListener(topics = "${spring.kafka.topics.comment-topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String event) {
        try {
            NotificationCommentEvent commentEvent = objectMapper.readValue(event, NotificationCommentEvent.class);
            log.info("Parsed event: {}", event);
            processEvent(commentEvent);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON: {}", event);
            throw new RuntimeException(e);
        }
    }
}
