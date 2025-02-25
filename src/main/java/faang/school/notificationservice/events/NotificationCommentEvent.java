package faang.school.notificationservice.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationCommentEvent implements Event {
    private Long authorId;
    private Long postAuthorId;
    private Long postId;
    private Long commentId;
    private String content;
}
