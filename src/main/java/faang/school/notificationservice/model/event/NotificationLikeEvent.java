package faang.school.notificationservice.model.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationLikeEvent implements Event {
    private Long postId;
    private Long userId;
    private Long authorId;
}