package faang.school.notificationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.event.NotificationEventStartEvent;
import faang.school.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class EventStartEventListener extends AbstractEventListener<NotificationEventStartEvent> {
    private final ObjectMapper objectMapper;

    public EventStartEventListener(UserServiceClient userServiceClient,
                             List<NotificationService> notificationServices,
                             MessageSource messageSource, ObjectMapper objectMapper) {
        super(userServiceClient, notificationServices, messageSource);
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${spring.kafka.topics.event-start-topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void listenEvent(String jsonEvent) {
        try {
            NotificationEventStartEvent event = objectMapper.readValue(jsonEvent, NotificationEventStartEvent.class);
            log.info("Received event start notification for eventId: {} with {} participants",
                    event.getEventId(), event.getUserIds().size());

            event.getUserIds().forEach(userId -> {
                NotificationEventStartEvent eventStartEvent = new NotificationEventStartEvent();
                eventStartEvent.setEventId(event.getEventId());
                eventStartEvent.setOwnerId(event.getOwnerId());
                eventStartEvent.setUserIds(List.of(userId));
                eventStartEvent.setStartTime(event.getStartTime());
                eventStartEvent.setMessage(event.getMessage());

                processEvent(eventStartEvent);
            });
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON: {}", jsonEvent);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getMessageKey() {
        return "StartEvent.notification";
    }

    @Override
    protected Object[] getMessageArgs(NotificationEventStartEvent event, UserDto user) {
        // The message arguments would typically include information about the event
        return new Object[]{event};
    }

    @Override
    protected Long getRecieverId(NotificationEventStartEvent event) {
        return event.getUserIds().get(0);
    }

    @Override
    protected Long getSenderId(NotificationEventStartEvent event) {
        return event.getOwnerId();
    }
}
