package faang.school.notificationservice.controller;

import faang.school.notificationservice.config.context.UserContext;
import faang.school.notificationservice.service.TelegramBindingCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Issues short-lived, single-use Telegram binding codes for the authenticated user
 * (NOT-04). The code is presented to the bot to bind the caller's own chat only.
 */
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/telegram")
@RestController
public class TelegramBindingController {

    private final TelegramBindingCodeService bindingCodeService;
    private final UserContext userContext;

    @PostMapping("/binding-code")
    public ResponseEntity<Map<String, String>> createBindingCode() {
        long userId = userContext.getUserId();
        String code = bindingCodeService.createCode(userId);
        return ResponseEntity.ok(Map.of("code", code));
    }
}
