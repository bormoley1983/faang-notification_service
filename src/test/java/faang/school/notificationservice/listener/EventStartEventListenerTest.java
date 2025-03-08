package faang.school.notificationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import faang.school.notificationservice.model.event.NotificationEventStartEvent;
import faang.school.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EventStartEventListenerTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private List<NotificationService> notificationServices;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EventStartEventListener eventStartEventListener;

    private UserDto mockUser;

    @BeforeEach
    public void setUp() {
        mockUser = new UserDto();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setPreference(PreferredContact.EMAIL);
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
        when(userServiceClient.getUser(any(Long.class))).thenReturn(mockUser);
        when(messageSource.getMessage(anyString(), any(Object[].class), any())).thenReturn("Event is starting");

        NotificationService mockNotificationService = mock(NotificationService.class);
        when(mockNotificationService.getPreferredContact()).thenReturn(PreferredContact.EMAIL);
        List<NotificationService> mockNotificationServices = List.of(mockNotificationService);
        Field notificationServicesField = AbstractEventListener.class.getDeclaredField("notificationServices");
        notificationServicesField.setAccessible(true);
        notificationServicesField.set(eventStartEventListener, mockNotificationServices);

        eventStartEventListener.listenEvent(jsonEvent);

        verify(userServiceClient, times(4)).getUser(any(Long.class));
        verify(messageSource, times(2)).getMessage(anyString(), any(Object[].class), any());
        verify(mockNotificationService, times(2)).send(any(UserDto.class), anyString());
    }
}
