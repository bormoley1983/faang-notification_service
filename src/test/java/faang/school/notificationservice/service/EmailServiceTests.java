package faang.school.notificationservice.service;

import faang.school.notificationservice.exception.NotificationDeliveryException;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTests {

    @Mock
    private JavaMailSender emailSender;

    @InjectMocks
    private EmailService emailService;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> mailMessageCaptor;

    private UserDto testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserDto();
        testUser.setId(42L);
        testUser.setEmail("test@example.com");
    }

    @Test
    void send_whenSenderSucceeds_preservesRecipientSubjectAndBody() {
        // Arrange
        String testMessage = "Test email content";

        // Act
        emailService.send(testUser, testMessage);

        // Assert: recipient, subject, and body preserved on the outgoing message
        verify(emailSender, times(1)).send(mailMessageCaptor.capture());
        SimpleMailMessage sentMessage = mailMessageCaptor.getValue();
        assertThat(sentMessage.getTo()).containsExactly("test@example.com");
        assertThat(sentMessage.getSubject()).isEqualTo("New notification");
        assertThat(sentMessage.getText()).isEqualTo(testMessage);
    }

    @Test
    void send_whenSenderFails_throwsDeliveryExceptionWithoutLeakingCredentials() {
        // Arrange: SMTP failure (e.g. auth error) must not leak the mail password
        doThrow(new org.springframework.mail.MailSendException("535 Authentication failed"))
                .when(emailSender).send(any(SimpleMailMessage.class));

        // Act / Assert: typed delivery exception with user id, no secret in the message
        assertThatThrownBy(() -> emailService.send(testUser, "msg"))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessage("Email delivery failed for user id 42")
                .hasRootCauseMessage("535 Authentication failed");
    }

    @Test
    void send_whenUserHasNoEmail_stillAttemptsSendWithNullRecipient() {
        // Arrange: missing destination — the mail sender is still invoked (failure surfaces there)
        testUser.setEmail(null);

        // Act / Assert: no NPE in the service itself; the message carries a null recipient
        assertThatCode(() -> emailService.send(testUser, "msg")).doesNotThrowAnyException();
        verify(emailSender).send(mailMessageCaptor.capture());
        // Spring wraps a single null address as [null]
        assertThat(mailMessageCaptor.getValue().getTo()).containsExactly((String) null);
    }

    @Test
    void getPreferredContact_whenQueried_returnsEmail() {
        // Act / Assert
        assertThat(emailService.getPreferredContact()).isEqualTo(PreferredContact.EMAIL);
    }
}
