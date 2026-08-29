package faang.school.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and validates short-lived, single-use binding codes for the Telegram bot.
 * A code is created by the authenticated application on behalf of a specific user
 * (NOT-04) and must be presented to the bot within {@link #TTL_SECONDS} to bind the
 * user's Telegram chat. This prevents an attacker from binding or hijacking another
 * user's notification destination by simply messaging the bot.
 */
@Slf4j
@Service
public class TelegramBindingCodeService {

    static final long TTL_SECONDS = 300;

    private record PendingBinding(String code, long userId, Instant expiresAt) {
    }

    private final Map<String, PendingBinding> pendingBindings = new ConcurrentHashMap<>();

    /**
     * Creates a binding code for the given user. The returned code must be presented
     * to the bot (e.g. typed as a message) within the TTL.
     */
    public String createCode(long userId) {
        evictExpired();
        String code = newCode();
        pendingBindings.put(code, new PendingBinding(code, userId, Instant.now().plusSeconds(TTL_SECONDS)));
        log.info("Created Telegram binding code for user id = {}", userId);
        return code;
    }

    /**
     * Validates and consumes a binding code. Returns the bound user id, or null if the
     * code is unknown, expired, or was already used (single use).
     */
    public Long consumeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        PendingBinding binding = pendingBindings.remove(normalized);
        if (binding == null) {
            log.warn("Unknown or already used Telegram binding code presented");
            return null;
        }
        if (Instant.now().isAfter(binding.expiresAt())) {
            log.warn("Expired Telegram binding code presented for user id = {}", binding.userId());
            return null;
        }
        return binding.userId();
    }

    private String newCode() {
        try {
            byte[] bytes = new byte[8];
            java.security.SecureRandom.getInstanceStrong().nextBytes(bytes);
            return HexFormat.of().formatHex(bytes).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No strong SecureRandom available", e);
        }
    }

    private void evictExpired() {
        Instant now = Instant.now();
        pendingBindings.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }
}
