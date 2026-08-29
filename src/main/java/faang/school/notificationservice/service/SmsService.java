package faang.school.notificationservice.service;

import com.vonage.client.VonageClient;
import com.vonage.client.sms.MessageStatus;
import com.vonage.client.sms.SmsSubmissionResponse;
import com.vonage.client.sms.messages.TextMessage;
import faang.school.notificationservice.config.sms.VonageConfig;
import faang.school.notificationservice.exception.NotificationDeliveryException;
import faang.school.notificationservice.exception.sms.SmsSendingException;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static faang.school.notificationservice.model.enums.PreferredContact.SMS;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService implements NotificationService {
    private final VonageConfig config;
    private final VonageClient vonageClient;

    @Override
    public void send(UserDto user, String message) {
        String phone = user.getPhone();

        TextMessage sms = new TextMessage(
                config.getFrom(),
                phone,
                message
        );

        SmsSubmissionResponse response = vonageClient.getSmsClient().submitMessage(sms);

        if (response.getMessages().get(0).getStatus() != MessageStatus.OK) {
            log.error("Failed to send SMS notification to user with id = {}", user.getId());
            throw new NotificationDeliveryException(
                    "SMS delivery failed for user id " + user.getId(),
                    new SmsSendingException("Vonage reported a non-OK status"));
        }

        log.info("Message sent successfully to user with id = {} via SMS", user.getId());
    }

    @Override
    public PreferredContact getPreferredContact() {
        return SMS;
    }
}