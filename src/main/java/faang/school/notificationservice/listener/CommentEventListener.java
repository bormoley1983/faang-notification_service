package faang.school.notificationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.dto.UserDto;
import faang.school.notificationservice.events.NotificationCommentEvent;
import faang.school.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CommentEventListener extends AbstractEventListener<NotificationCommentEvent> {

    public CommentEventListener(UserServiceClient userServiceClient, List<NotificationService> notificationServices, MessageSource messageSource) {
        super(userServiceClient, notificationServices, messageSource);
    }

    @Override
    protected String getMessageKey() {
        return "comment.new";
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

    @KafkaListener(topics = "${spring.kafka.topics.notifications-comment-topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String event) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            NotificationCommentEvent commentEvent = objectMapper.readValue(event, NotificationCommentEvent.class);
            processEvent(commentEvent);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON: {}", event);
            throw new RuntimeException(e);
        }
    }
}
