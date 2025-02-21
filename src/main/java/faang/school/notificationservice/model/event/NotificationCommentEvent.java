package faang.school.notificationservice.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationCommentEvent implements Event{
    private long postId;
    private long authorId;
    private long commentId;
    private String content;
}
