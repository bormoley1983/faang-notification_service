package faang.school.notificationservice.service;

import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import static faang.school.notificationservice.model.enums.PreferredContact.EMAIL;

@Slf4j
@RequiredArgsConstructor
@Service
public class EmailService implements NotificationService {

    private final JavaMailSender emailSender;

    @Override
    public void send(UserDto user, String message) {
        String recieverEmail = user.getEmail();

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(recieverEmail);
        msg.setSubject("New notification");
        msg.setText(message);
        emailSender.send(msg);
        log.info("Notification to mail: {}. With content {}. Was sent", user.getEmail(), message);
    }

    @Override
    public PreferredContact getPreferredContact() {
        return EMAIL;
    }
}
