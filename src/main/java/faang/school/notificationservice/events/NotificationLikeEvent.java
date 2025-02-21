package faang.school.notificationservice.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLikeEvent implements Event {
    private Long postId;
    private Long userId;
    private Long authorId;
}
