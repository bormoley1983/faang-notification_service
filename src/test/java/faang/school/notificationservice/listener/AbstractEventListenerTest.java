package faang.school.notificationservice.listener;

import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.exception.NotificationDeliveryException;
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
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the shared {@link AbstractEventListener} workflow through a concrete
 * test listener: recipient resolution, sender exclusion, deduplication, bulk user
 * fetch, locale handling, strategy selection/fallback, and per-recipient isolation.
 */
@ExtendWith(MockitoExtension.class)
class AbstractEventListenerTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private MessageSource messageSource;

    @Mock
    private NotificationService emailService;

    @Mock
    private NotificationService smsService;

    private TestListener listener;

    /** Concrete listener with fixed behavior so the abstract workflow is testable. */
    static class TestListener extends AbstractEventListener<NotificationCommentEvent> {
        TestListener(UserServiceClient userServiceClient,
                     List<NotificationService> notificationServices,
                     MessageSource messageSource) {
            super(userServiceClient, notificationServices, messageSource);
        }

        @Override
        protected String getMessageKey() {
            return "comment.notification";
        }

        @Override
        protected Object[] getMessageArgs(NotificationCommentEvent event, UserDto user) {
            return new Object[]{user == null ? null : user.getUsername(), event.getContent()};
        }

        @Override
        protected Long getRecieverId(NotificationCommentEvent event) {
            return event.getPostAuthorId();
        }

        @Override
        protected Long getSenderId(NotificationCommentEvent event) {
            return event.getAuthorId();
        }
    }

    private NotificationCommentEvent event(long authorId, long postAuthorId) {
        return new NotificationCommentEvent(10L, authorId, postAuthorId, 20L, "nice post");
    }

    private UserDto user(long id, PreferredContact preference) {
        UserDto dto = new UserDto();
        dto.setId(id);
        dto.setUsername("user" + id);
        dto.setPreference(preference);
        return dto;
    }

    @BeforeEach
    void setUp() {
        // Lenient: not every test exercises both strategies (e.g. sender-exclusion short-circuits)
        lenient().when(emailService.getPreferredContact()).thenReturn(PreferredContact.EMAIL);
        lenient().when(smsService.getPreferredContact()).thenReturn(PreferredContact.SMS);
        listener = new TestListener(userServiceClient, List.of(emailService, smsService), messageSource);
    }

    @Test
    void processEvent_whenRecipientPresent_sendsViaPreferredChannel() {
        // Arrange
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(user(5L, PreferredContact.SMS)));
        when(messageSource.getMessage(eq("comment.notification"), any(Object[].class), eq(null)))
                .thenReturn("user5 commented");

        // Act
        listener.processEvent(event(1L, 5L));

        // Assert: one bulk fetch, message resolved once with default locale, SMS used
        verify(userServiceClient, times(1)).getUsersByIds(anyList());
        verify(messageSource, times(1)).getMessage(eq("comment.notification"), any(Object[].class), eq(null));
        verify(smsService).send(any(UserDto.class), eq("user5 commented"));
        verify(emailService, never()).send(any(), any());
    }

    @Test
    void processEvent_whenSenderIsRecipient_skipsAndFetchesNothing() {
        // Arrange: only recipient is the sender themselves
        NotificationCommentEvent e = event(7L, 7L);

        // Act
        listener.processEvent(e);

        // Assert: no user fetch, no message resolution, no sends
        verifyNoInteractions(userServiceClient);
        verifyNoInteractions(messageSource);
        verify(emailService, never()).send(any(), any());
        verify(smsService, never()).send(any(), any());
    }

    @Test
    void processEvent_whenRecipientListEmpty_skipsSilently() {
        // Arrange: a listener whose recipient resolution yields no recipients
        EmptyRecipientListener empty = new EmptyRecipientListener(userServiceClient,
                List.of(emailService), messageSource);

        // Act
        empty.processEvent(event(1L, 5L));

        // Assert: empty recipient list short-circuits before any collaborator call
        verifyNoInteractions(userServiceClient);
        verifyNoInteractions(messageSource);
    }

    /** Listener with no recipients — models events whose recipient resolution yields nothing. */
    static class EmptyRecipientListener extends TestListener {
        EmptyRecipientListener(UserServiceClient c, List<NotificationService> s, MessageSource m) {
            super(c, s, m);
        }

        @Override
        protected List<Long> getRecipientIds(NotificationCommentEvent event) {
            return List.of();
        }
    }

    @Test
    void processEvent_whenBulkFetchMissesRecipient_skipsThatRecipient() {
        // Arrange: user service returns no matching user
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of());
        when(messageSource.getMessage(eq("comment.notification"), any(Object[].class), eq(null)))
                .thenReturn("msg");

        // Act
        listener.processEvent(event(1L, 5L));

        // Assert: fetch happened, but no channel was invoked
        verify(userServiceClient).getUsersByIds(anyList());
        verify(emailService, never()).send(any(), any());
        verify(smsService, never()).send(any(), any());
    }

    @Test
    void sendNotification_whenPreferenceUnsupported_fallsBackToEmail() {
        // Arrange: user prefers TELEGRAM but only EMAIL is registered → NOT-07 EMAIL fallback path
        AbstractEventListener<NotificationCommentEvent> emailOnly =
                new TestListener(userServiceClient, List.of(emailService), messageSource);
        UserDto recipient = user(5L, PreferredContact.TELEGRAM);

        // Act
        emailOnly.sendNotification(recipient, "msg");

        // Assert: no TELEGRAM strategy → falls back to the EMAIL strategy
        verify(emailService).send(recipient, "msg");
    }

    @Test
    void processEvent_whenPreferenceMatchesRegisteredStrategy_usesIt() {
        // Arrange: user prefers SMS and an SMS strategy is registered
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(user(5L, PreferredContact.SMS)));
        when(messageSource.getMessage(eq("comment.notification"), any(Object[].class), eq(null)))
                .thenReturn("msg");

        // Act
        listener.processEvent(event(1L, 5L));

        // Assert: the matching strategy is used directly (no fallback needed)
        verify(smsService).send(any(UserDto.class), eq("msg"));
        verify(emailService, never()).send(any(), any());
    }

    @Test
    void sendNotification_whenNoStrategyAvailable_throwsIllegalArgument() {
        // Arrange: only SMS registered, user prefers TELEGRAM → no fallback possible
        AbstractEventListener<NotificationCommentEvent> smsOnly =
                new TestListener(userServiceClient, List.of(smsService), messageSource);
        UserDto recipient = user(5L, PreferredContact.TELEGRAM);

        // Act / Assert: strategy resolution failure is a typed programming error.
        // (processEvent would catch it per-recipient via NOT-06 isolation; the direct call
        // pins the contract of sendNotification itself.)
        assertThatThrownBy(() -> smsOnly.sendNotification(recipient, "msg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No suitable notificationService found")
                .hasMessageContaining("5");
    }

    @Test
    void processEvent_whenLocaleProvided_resolvesWithThatLocale() {
        // Arrange: listener that returns a fixed locale (NOT-09 hook)
        LocaleListener localeListener = new LocaleListener(userServiceClient, List.of(emailService), messageSource);
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(user(5L, PreferredContact.EMAIL)));
        when(messageSource.getMessage(eq("comment.notification"), any(Object[].class), eq(Locale.GERMAN)))
                .thenReturn("de-msg");

        // Act
        localeListener.processEvent(event(1L, 5L));

        // Assert: message resolved with the event locale, not the default
        verify(messageSource).getMessage(eq("comment.notification"), any(Object[].class), eq(Locale.GERMAN));
        verify(emailService).send(any(UserDto.class), eq("de-msg"));
    }

    static class LocaleListener extends TestListener {
        LocaleListener(UserServiceClient c, List<NotificationService> s, MessageSource m) {
            super(c, s, m);
        }

        @Override
        protected Locale getMessageLocale(NotificationCommentEvent event) {
            return Locale.GERMAN;
        }
    }

    @Test
    void processEvent_whenOneRecipientFails_continuesWithRemainingRecipients() {
        // Arrange: fan-out listener with two recipients; first fails, second succeeds
        FanOutListener fanOut = new FanOutListener(userServiceClient, List.of(emailService), messageSource);
        UserDto bad = user(5L, PreferredContact.EMAIL);
        UserDto good = user(6L, PreferredContact.EMAIL);
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(bad, good));
        when(messageSource.getMessage(eq("comment.notification"), any(Object[].class), eq(null)))
                .thenReturn("msg");
        org.mockito.Mockito.doThrow(new NotificationDeliveryException("Email delivery failed for user id 5"))
                .doNothing()
                .when(emailService).send(any(UserDto.class), any());

        // Act: must not throw — per-recipient isolation (NOT-06)
        fanOut.processEvent(event(1L, 5L));

        // Assert: both recipients attempted, failure did not stop the loop
        verify(emailService, times(2)).send(any(UserDto.class), eq("msg"));
    }

    /** Fan-out listener returning two fixed recipients. */
    static class FanOutListener extends TestListener {
        FanOutListener(UserServiceClient c, List<NotificationService> s, MessageSource m) {
            super(c, s, m);
        }

        @Override
        protected List<Long> getRecipientIds(NotificationCommentEvent event) {
            return List.of(5L, 6L);
        }
    }

    @Test
    void processEvent_whenDuplicateRecipientIds_sendsOncePerDistinctUser() {
        // Arrange: duplicate ids collapse to one recipient
        DuplicateListener dup = new DuplicateListener(userServiceClient, List.of(emailService), messageSource);
        when(userServiceClient.getUsersByIds(anyList())).thenReturn(List.of(user(5L, PreferredContact.EMAIL)));
        when(messageSource.getMessage(eq("comment.notification"), any(Object[].class), eq(null)))
                .thenReturn("msg");

        // Act
        dup.processEvent(event(1L, 5L));

        // Assert: distinct() keeps a single recipient → exactly one send
        verify(emailService, times(1)).send(any(UserDto.class), eq("msg"));
    }

    static class DuplicateListener extends TestListener {
        DuplicateListener(UserServiceClient c, List<NotificationService> s, MessageSource m) {
            super(c, s, m);
        }

        @Override
        protected List<Long> getRecipientIds(NotificationCommentEvent event) {
            return List.of(5L, 5L, 5L);
        }
    }

    @Test
    void processEvent_whenBulkFetchFails_propagatesClientFailure() {
        // Arrange: user service call fails (Feign error)
        when(userServiceClient.getUsersByIds(anyList()))
                .thenThrow(new RuntimeException("user-service unavailable"));

        // Act / Assert: no per-recipient isolation for the fetch itself — it propagates
        assertThatThrownBy(() -> listener.processEvent(event(1L, 5L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("user-service unavailable");
        verify(emailService, never()).send(any(), any());
    }

    @Test
    void sendNotification_whenUserPrefersEmail_usesEmailStrategy() {
        // Arrange
        UserDto recipient = user(9L, PreferredContact.EMAIL);

        // Act
        listener.sendNotification(recipient, "hello");

        // Assert
        verify(emailService).send(recipient, "hello");
        verify(smsService, never()).send(any(), any());
    }
}
