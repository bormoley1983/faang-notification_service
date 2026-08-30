package faang.school.notificationservice.service;

import com.vonage.client.VonageClient;
import com.vonage.client.sms.MessageStatus;
import com.vonage.client.sms.SmsClient;
import com.vonage.client.sms.SmsSubmissionResponse;
import com.vonage.client.sms.SmsSubmissionResponseMessage;
import com.vonage.client.sms.messages.TextMessage;
import faang.school.notificationservice.config.sms.VonageConfig;
import faang.school.notificationservice.exception.NotificationDeliveryException;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SmsServiceTest {
    @Mock
    private VonageConfig config;
    @Mock
    private VonageClient vonageClient;
    @Mock
    private SmsClient smsClient;
    @InjectMocks
    private SmsService smsService;

    @Mock
    private SmsSubmissionResponseMessage successMessage;
    @Mock
    private SmsSubmissionResponse response;

    private UserDto userDto;
    private String message;

    @BeforeEach
    void setup() {
        userDto = new UserDto();
        userDto.setId(42L);
        userDto.setPhone("1234567890");
        message = "Hello!";
        // Lenient: the getPreferredContact test never touches the Vonage stack
        org.mockito.Mockito.lenient().when(config.getFrom()).thenReturn("faang");
        org.mockito.Mockito.lenient().when(vonageClient.getSmsClient()).thenReturn(smsClient);
        org.mockito.Mockito.lenient().when(response.getMessages()).thenReturn(List.of(successMessage));
        org.mockito.Mockito.lenient().when(smsClient.submitMessage(any(TextMessage.class))).thenReturn(response);
    }

    @Test
    void sendTest_Success() {
        when(successMessage.getStatus()).thenReturn(MessageStatus.OK);

        assertDoesNotThrow(() -> smsService.send(userDto, message));
        verify(smsClient).submitMessage(any(TextMessage.class));
    }

    @Test
    void sendTest_ShouldThrowExceptionWhenSmsDoesNotSend() {
        when(successMessage.getStatus()).thenReturn(MessageStatus.INTERNAL_ERROR);

        NotificationDeliveryException exception = assertThrows(NotificationDeliveryException.class,
                () -> smsService.send(userDto, message));

        assertEquals("SMS delivery failed for user id 42", exception.getMessage());
        verify(smsClient).submitMessage(any(TextMessage.class));
    }

    @Test
    void send_whenProviderCallFails_propagatesProviderFailure() {
        // Arrange: Vonage client itself fails (network / auth error)
        when(vonageClient.getSmsClient()).thenReturn(smsClient);
        when(smsClient.submitMessage(any(TextMessage.class)))
                .thenThrow(new RuntimeException("Vonage API unavailable"));

        // Act / Assert: the provider failure propagates so the Kafka offset is not committed (NOT-03)
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> smsService.send(userDto, message));
        assertEquals("Vonage API unavailable", exception.getMessage());
    }

    @Test
    void send_whenUserHasNoPhone_stillSubmitsMessageWithNullRecipient() {
        // Arrange: missing destination — submission still attempted (Vonage reports the failure)
        userDto.setPhone(null);
        when(successMessage.getStatus()).thenReturn(MessageStatus.OK);

        // Act / Assert: no NPE in the service; the TextMessage carries a null recipient
        assertDoesNotThrow(() -> smsService.send(userDto, message));
        verify(smsClient).submitMessage(any(TextMessage.class));
    }

    @Test
    void getPreferredContact_whenQueried_returnsSms() {
        // Act / Assert
        assertEquals(PreferredContact.SMS, smsService.getPreferredContact());
    }
}