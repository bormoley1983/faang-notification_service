package faang.school.notificationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.exception.EventDeserializationException;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import faang.school.notificationservice.model.event.NotificationCommentEvent;
import faang.school.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentEventListenerTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotificationService emailService;

    private CommentEventListener listener;

    private UserDto postAuthor;

    @BeforeEach
    void setUp() {
        // Lenient: sender-exclusion and malformed-payload tests never reach strategy selection
        lenient().when(emailService.getPreferredContact()).thenReturn(PreferredContact.EMAIL);
        postAuthor = new UserDto();
        postAuthor.setId(10L);
        postAuthor.setUsername("author");
        postAuthor.setPreference(PreferredContact.EMAIL);
        listener = new CommentEventListener(userServiceClient, List.of(emailService), messageSource, objectMapper);
    }

    @Test
    void onMessage_whenValidPayload_notifiesPostAuthorWithCommentContent() throws JsonProcessingException {
        // Arrange
        String json = "{\"postId\":1,\"authorId\":2,\"postAuthorId\":10,\"commentId\":3,\"content\":\"nice\"}";
        NotificationCommentEvent event = new NotificationCommentEvent(1L, 2L, 10L, 3L, "nice");
        when(objectMapper.readValue(json, NotificationCommentEvent.class)).thenReturn(event);
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(postAuthor));
        when(messageSource.getMessage(eq("comment.notification"), any(Object[].class), eq(null)))
                .thenReturn("author commented: nice");

        // Act
        listener.onMessage(json);

        // Assert: correct key, args (empty username — the user arg is null at resolution time), single delivery
        verify(userServiceClient).getUsersByIds(anyList());
        verify(messageSource).getMessage(eq("comment.notification"), eq(new Object[]{"", "nice"}), eq(null));
        verify(emailService).send(postAuthor, "author commented: nice");
    }

    @Test
    void onMessage_whenCommenterIsPostAuthor_noNotificationSent() throws JsonProcessingException {
        // Arrange: authorId == postAuthorId → sender excluded from recipients
        String json = "{\"postId\":1,\"authorId\":10,\"postAuthorId\":10,\"commentId\":3,\"content\":\"self\"}";
        NotificationCommentEvent event = new NotificationCommentEvent(1L, 10L, 10L, 3L, "self");
        when(objectMapper.readValue(json, NotificationCommentEvent.class)).thenReturn(event);

        // Act
        listener.onMessage(json);

        // Assert: no user fetch and no send
        verify(userServiceClient, never()).getUsersByIds(anyList());
        verify(emailService, never()).send(any(), any());
    }

    @Test
    void onMessage_whenPayloadMalformed_throwsDeserializationException() throws JsonProcessingException {
        // Arrange
        when(objectMapper.readValue("{not json", NotificationCommentEvent.class))
                .thenThrow(new JsonProcessingException("bad") {});

        // Act / Assert: NOT-08 — raw payload is not leaked; typed exception propagates to Kafka retry/DLQ
        assertThatThrownBy(() -> listener.onMessage("{not json"))
                .isInstanceOf(EventDeserializationException.class)
                .hasMessage("Failed to deserialize NotificationCommentEvent");
        verify(userServiceClient, never()).getUsersByIds(anyList());
    }

    @Test
    void onMessage_whenDeliveryFails_failureIsIsolatedPerRecipient() throws JsonProcessingException {
        // Arrange: a channel failure for one recipient must NOT propagate (NOT-06) —
        // processEvent catches RuntimeException per recipient so already-delivered
        // messages are not duplicated by a Kafka record retry.
        String json = "{\"postId\":1,\"authorId\":2,\"postAuthorId\":10,\"commentId\":3,\"content\":\"nice\"}";
        NotificationCommentEvent event = new NotificationCommentEvent(1L, 2L, 10L, 3L, "nice");
        when(objectMapper.readValue(json, NotificationCommentEvent.class)).thenReturn(event);
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(postAuthor));
        when(messageSource.getMessage(eq("comment.notification"), any(Object[].class), eq(null)))
                .thenReturn("msg");
        org.mockito.Mockito.doThrow(new faang.school.notificationservice.exception.NotificationDeliveryException(
                "Email delivery failed for user id 10"))
                .when(emailService).send(any(), any());

        // Act / Assert: the delivery exception is swallowed and logged, not rethrown
        assertThatCode(() -> listener.onMessage(json)).doesNotThrowAnyException();
        verify(emailService).send(postAuthor, "msg");
    }
}
