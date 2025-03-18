package faang.school.notificationservice.model.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationEventStartEvent implements Event {
    private Long eventId;
    private Long ownerId;
    private List<Long> userIds;
    private LocalDateTime startTime;
    private String message;
}