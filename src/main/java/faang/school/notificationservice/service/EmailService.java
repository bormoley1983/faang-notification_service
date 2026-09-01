package faang.school.notificationservice.service;

import faang.school.notificationservice.exception.NotificationDeliveryException;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import static faang.school.notificationservice.model.enums.PreferredContact.EMAIL;

@Slf4j
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(prefix = "notification.channels.email", name = "enabled", havingValue = "true")
public class EmailService implements NotificationService {

    private final JavaMailSender emailSender;

    @Override
    public void send(UserDto user, String message) {
        String recieverEmail = user.getEmail();

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(recieverEmail);
        msg.setSubject("New notification");
        msg.setText(message);
        try {
            emailSender.send(msg);
        } catch (RuntimeException e) {
            log.error("Failed to send email notification to user with id = {}", user.getId(), e);
            throw new NotificationDeliveryException(
                    "Email delivery failed for user id " + user.getId(), e);
        }
        log.info("Notification was sent to user with id = {} via EMAIL", user.getId());
    }

    @Override
    public PreferredContact getPreferredContact() {
        return EMAIL;
    }
}
