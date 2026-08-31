package faang.school.notificationservice.client;

import faang.school.notificationservice.model.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-service", url = "http://${user-service.host}:${user-service.port}")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{id}")
    UserDto getUser(@PathVariable long id);

    @PutMapping("/api/v1/users/updateTelegramUserId")
    UserDto updateTelegramUserId(@RequestParam String telegramUsername,
                                 @RequestParam String telegramChatId);

    @PutMapping("/api/v1/users/{id}/telegram-chat")
    UserDto bindTelegramChat(@PathVariable("id") long userId,
                             @RequestParam String telegramChatId);

    /**
     * The user service exposes {@code POST /api/v1/users/list}, which accepts a list of
     * users (only the id is used) and returns the matching {@code List<UserDto>} — a plain
     * array, so no Page deserialization is needed on this side.
     */
    @PostMapping("/api/v1/users/list")
    List<UserDto> getUsersByIds(@RequestBody List<UserDto> users);
}
