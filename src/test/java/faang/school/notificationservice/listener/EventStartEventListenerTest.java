package faang.school.notificationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.exception.EventDeserializationException;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import faang.school.notificationservice.model.event.NotificationEventStartEvent;
import faang.school.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EventStartEventListenerTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotificationService emailService;

    private EventStartEventListener eventStartEventListener;

    @BeforeEach
    public void setUp() {
        // Manual construction: @InjectMocks cannot inject a single mock into the
        // List<NotificationService> constructor parameter. Lenient because tests that
        // never reach strategy selection (malformed payload, no recipients) don't use it.
        lenient().when(emailService.getPreferredContact()).thenReturn(PreferredContact.EMAIL);
        eventStartEventListener = new EventStartEventListener(
                userServiceClient, List.of(emailService), messageSource, objectMapper);
    }

    @Test
    public void testListenEvent() throws JsonProcessingException, NoSuchFieldException, IllegalAccessException {
        String jsonEvent =
                "{\"eventId\":1,\"ownerId\":2,\"userIds\":[3,4],\"startTime\":\"2023-10-10T10:00:00\"" +
                        ",\"message\":\"Event is starting\"}";
        NotificationEventStartEvent event =
                new NotificationEventStartEvent(1L, 2L, List.of(3L, 4L),
                        LocalDateTime.parse("2023-10-10T10:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "Event is starting");

        when(objectMapper.readValue(jsonEvent, NotificationEventStartEvent.class)).thenReturn(event);

        // NOT-06: recipients are fetched in one bulk call; sender (ownerId = 2) is filtered out.
        UserDto recipient3 = new UserDto();
        recipient3.setId(3L);
        recipient3.setUsername("user3");
        recipient3.setPreference(PreferredContact.EMAIL);
        UserDto recipient4 = new UserDto();
        recipient4.setId(4L);
        recipient4.setUsername("user4");
        recipient4.setPreference(PreferredContact.EMAIL);
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(recipient3, recipient4));

        // Message is resolved once per event (NOT-09: default locale).
        when(messageSource.getMessage(anyString(), any(Object[].class), any())).thenReturn("Event is starting");

        eventStartEventListener.listenEvent(jsonEvent);

        verify(userServiceClient, times(1)).getUsersByIds(anyList());
        verify(messageSource, times(1)).getMessage(anyString(), any(Object[].class), any());
        verify(emailService, times(2)).send(any(UserDto.class), anyString());
    }

    @Test
    public void testListenEvent_whenPayloadMalformed_throwsDeserializationException() throws JsonProcessingException {
        // Arrange: NOT-08 — raw payload is not leaked; typed exception propagates to Kafka retry/DLQ
        when(objectMapper.readValue("{not json", NotificationEventStartEvent.class))
                .thenThrow(new JsonProcessingException("bad") {});

        // Act / Assert
        assertThatThrownBy(() -> eventStartEventListener.listenEvent("{not json"))
                .isInstanceOf(EventDeserializationException.class)
                .hasMessage("Failed to deserialize NotificationEventStartEvent");
        verify(userServiceClient, times(0)).getUsersByIds(anyList());
    }

    @Test
    public void testListenEvent_whenOnlyOwnerIsParticipant_noNotificationSent() throws JsonProcessingException {
        // Arrange: sender (ownerId) is filtered out → no recipients remain
        String jsonEvent =
                "{\"eventId\":1,\"ownerId\":2,\"userIds\":[2],\"startTime\":\"2023-10-10T10:00:00\""
                        + ",\"message\":\"Event is starting\"}";
        NotificationEventStartEvent event =
                new NotificationEventStartEvent(1L, 2L, List.of(2L),
                        LocalDateTime.parse("2023-10-10T10:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "Event is starting");
        when(objectMapper.readValue(jsonEvent, NotificationEventStartEvent.class)).thenReturn(event);

        // Act / Assert: no bulk user fetch and no message resolution happen
        eventStartEventListener.listenEvent(jsonEvent);

        verify(userServiceClient, times(0)).getUsersByIds(anyList());
        verify(emailService, times(0)).send(any(UserDto.class), anyString());
    }

    @Test
    public void testListenEvent_whenRecipientMissingFromBulkFetch_isSkipped() throws JsonProcessingException {
        // Arrange: bulk fetch returns only one of the two recipients → missing one is skipped (NOT-06)
        String jsonEvent =
                "{\"eventId\":1,\"ownerId\":2,\"userIds\":[3,4],\"startTime\":\"2023-10-10T10:00:00\""
                        + ",\"message\":\"Event is starting\"}";
        NotificationEventStartEvent event =
                new NotificationEventStartEvent(1L, 2L, List.of(3L, 4L),
                        LocalDateTime.parse("2023-10-10T10:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "Event is starting");
        when(objectMapper.readValue(jsonEvent, NotificationEventStartEvent.class)).thenReturn(event);

        UserDto recipient3 = new UserDto();
        recipient3.setId(3L);
        recipient3.setUsername("user3");
        recipient3.setPreference(PreferredContact.EMAIL);
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(recipient3));
        when(messageSource.getMessage(anyString(), any(Object[].class), any())).thenReturn("Event is starting");

        // Act / Assert: only the resolvable recipient receives a notification
        eventStartEventListener.listenEvent(jsonEvent);

        verify(emailService, times(1)).send(recipient3, "Event is starting");
    }

    @Test
    public void testListenEvent_whenDeliveryFails_failureIsIsolatedPerRecipient() throws JsonProcessingException {
        // Arrange: a channel failure for one recipient must NOT propagate (NOT-06)
        String jsonEvent =
                "{\"eventId\":1,\"ownerId\":2,\"userIds\":[3],\"startTime\":\"2023-10-10T10:00:00\""
                        + ",\"message\":\"Event is starting\"}";
        NotificationEventStartEvent event =
                new NotificationEventStartEvent(1L, 2L, List.of(3L),
                        LocalDateTime.parse("2023-10-10T10:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "Event is starting");
        when(objectMapper.readValue(jsonEvent, NotificationEventStartEvent.class)).thenReturn(event);

        UserDto recipient3 = new UserDto();
        recipient3.setId(3L);
        recipient3.setUsername("user3");
        recipient3.setPreference(PreferredContact.EMAIL);
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(recipient3));
        when(messageSource.getMessage(anyString(), any(Object[].class), any())).thenReturn("Event is starting");
        org.mockito.Mockito.doThrow(new RuntimeException("Email delivery failed for user id 3"))
                .when(emailService).send(any(UserDto.class), anyString());

        // Act / Assert: the delivery exception is swallowed and logged, not rethrown
        assertThatCode(() -> eventStartEventListener.listenEvent(jsonEvent)).doesNotThrowAnyException();
        verify(emailService, times(1)).send(recipient3, "Event is starting");
    }

    @Test
    public void testListenEvent_whenMessageResolved_argsAreScalarNotEventPojo() throws JsonProcessingException {
        // Arrange: The message args must be explicit scalar values (the event id),
        // not the whole NotificationEventStartEvent POJO whose Lombok toString() would dump
        // every field into the notification body.
        String jsonEvent =
                "{\"eventId\":42,\"ownerId\":2,\"userIds\":[3],\"startTime\":\"2023-10-10T10:00:00\""
                        + ",\"message\":\"Event is starting\"}";
        NotificationEventStartEvent event =
                new NotificationEventStartEvent(42L, 2L, List.of(3L),
                        LocalDateTime.parse("2023-10-10T10:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "Event is starting");
        when(objectMapper.readValue(jsonEvent, NotificationEventStartEvent.class)).thenReturn(event);

        UserDto recipient3 = new UserDto();
        recipient3.setId(3L);
        recipient3.setUsername("user3");
        recipient3.setPreference(PreferredContact.EMAIL);
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(recipient3));
        when(messageSource.getMessage(anyString(), any(Object[].class), any()))
                .thenAnswer(invocation -> {
                    Object[] args = invocation.getArgument(1);
                    // Simulate MessageSource rendering: "Event {0} has started." with the scalar id.
                    return "Event " + args[0] + " has started.";
                });

        // Act
        eventStartEventListener.listenEvent(jsonEvent);

        // Assert: the resolved message is a clean, readable sentence using the scalar id —
        // no Lombok field dump appears in the body.
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(messageSource).getMessage(eq("event.start.notification"), argsCaptor.capture(), any());
        Object[] args = argsCaptor.getValue();
        assertThat(args).hasSize(1);
        assertThat(args[0]).isEqualTo(42L);
        assertThat(args[0]).isNotInstanceOf(NotificationEventStartEvent.class);

        verify(emailService, times(1)).send(recipient3, "Event 42 has started.");
    }
}
