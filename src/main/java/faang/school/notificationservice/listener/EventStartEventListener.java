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

            // NOT-06: process the whole fan-out in one pass so users are fetched in a
            // single bulk call and each recipient failure is isolated.
            processEvent(event);
        } catch (JsonProcessingException e) {
            log.error("Error parsing event start notification payload, length = {}", jsonEvent.length(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getMessageKey() {
        return "event.start.notification";
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
    protected List<Long> getRecipientIds(NotificationEventStartEvent event) {
        // NOT-06: fan out to all participants in one bulk user fetch.
        return event.getUserIds();
    }

    @Override
    protected Long getSenderId(NotificationEventStartEvent event) {
        return event.getOwnerId();
    }
}
