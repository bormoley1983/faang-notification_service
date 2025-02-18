package faang.school.notificationservice.service;

import faang.school.notificationservice.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class NotificationServiceHandler {

    private final Map<String, NotificationService> notificationServices;

    public void sendNotification(UserDto user, String message) {
        NotificationService service = notificationServices.get(user.getPreference().name());

        if (service == null) {
            log.warn("No NotificationService found for preference: {}", user.getPreference());
            return;
        }

        log.info("Sending notification to {} via {}", user.getUsername(), user.getPreference());
        service.send(user, message);
    }
}