package faang.school.notificationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.exception.EventDeserializationException;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import faang.school.notificationservice.model.event.NotificationLikeEvent;
import faang.school.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeEventListenerTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotificationService emailService;

    private LikeEventListener listener;

    private UserDto postAuthor;

    @BeforeEach
    void setUp() {
        // Lenient: sender-exclusion and malformed-payload tests never reach strategy selection
        lenient().when(emailService.getPreferredContact()).thenReturn(PreferredContact.EMAIL);
        postAuthor = new UserDto();
        postAuthor.setId(10L);
        postAuthor.setUsername("author");
        postAuthor.setPreference(PreferredContact.EMAIL);
        listener = new LikeEventListener(userServiceClient, List.of(emailService), messageSource, objectMapper);
    }

    @Test
    void onMessage_whenValidPayload_notifiesPostAuthorWithLikeArgs() throws JsonProcessingException {
        // Arrange: like event — recipient is the post author (authorId), sender is the liker (userId)
        String json = "{\"postId\":1,\"userId\":2,\"authorId\":10}";
        NotificationLikeEvent event = new NotificationLikeEvent();
        event.setPostId(1L);
        event.setUserId(2L);
        event.setAuthorId(10L);
        when(objectMapper.readValue(json, NotificationLikeEvent.class)).thenReturn(event);
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(postAuthor));
        when(messageSource.getMessage(eq("like.notification"), any(Object[].class), eq(null)))
                .thenReturn("user 2 liked post 1");

        // Act
        listener.onMessage(json);

        // Assert: args are (liker id, post id) and delivery goes to the post author
        verify(messageSource).getMessage(eq("like.notification"), eq(new Object[]{2L, 1L}), eq(null));
        verify(emailService).send(postAuthor, "user 2 liked post 1");
    }

    @Test
    void onMessage_whenLikerIsPostAuthor_noNotificationSent() throws JsonProcessingException {
        // Arrange: userId == authorId → sender excluded
        String json = "{\"postId\":1,\"userId\":10,\"authorId\":10}";
        NotificationLikeEvent event = new NotificationLikeEvent();
        event.setPostId(1L);
        event.setUserId(10L);
        event.setAuthorId(10L);
        when(objectMapper.readValue(json, NotificationLikeEvent.class)).thenReturn(event);

        // Act
        listener.onMessage(json);

        // Assert: no fetch, no send
        verify(userServiceClient, never()).getUsersByIds(anyList());
        verify(emailService, never()).send(any(), any());
    }

    @Test
    void onMessage_whenPayloadMalformed_throwsDeserializationException() throws JsonProcessingException {
        // Arrange
        when(objectMapper.readValue("[]", NotificationLikeEvent.class))
                .thenThrow(new JsonProcessingException("bad") {});

        // Act / Assert
        assertThatThrownBy(() -> listener.onMessage("[]"))
                .isInstanceOf(EventDeserializationException.class)
                .hasMessage("Failed to deserialize NotificationLikeEvent");
    }
}
