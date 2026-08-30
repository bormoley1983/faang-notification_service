package faang.school.notificationservice.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers code lifecycle: creation, single-use consumption, unknown/blank codes,
 * normalization, and expiry. Expiry is exercised by backdating the stored binding's
 * expiry via reflection (the service uses {@link Instant#now()} internally).
 */
class TelegramBindingCodeServiceTest {

    private final TelegramBindingCodeService service = new TelegramBindingCodeService();

    @SuppressWarnings("unchecked")
    private Map<String, Object> pendingBindings() throws Exception {
        Field field = TelegramBindingCodeService.class.getDeclaredField("pendingBindings");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(service);
    }

    /** Replaces the stored binding for {@code code} with one whose expiry is {@code expiresAt}. */
    private void setExpiry(String code, Instant expiresAt) throws Exception {
        Map<String, Object> bindings = pendingBindings();
        Object entry = bindings.get(code);
        Class<?> recordClass = entry.getClass();
        Constructor<?> ctor = recordClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Field codeField = recordClass.getDeclaredField("code");
        Field userIdField = recordClass.getDeclaredField("userId");
        codeField.setAccessible(true);
        userIdField.setAccessible(true);
        bindings.put(code, ctor.newInstance(codeField.get(entry), userIdField.get(entry), expiresAt));
    }

    @Test
    void createCode_whenCalled_returns16CharUppercaseHexAndStoresPendingBinding() throws Exception {
        // Act
        String code = service.createCode(42L);

        // Assert: 8 random bytes → 16 uppercase hex chars, stored for the right user
        assertThat(code).hasSize(16).matches("[0-9A-F]{16}");
        assertThat(pendingBindings()).containsKey(code);
    }

    @Test
    void consumeCode_whenValidCode_returnsUserIdAndConsumesIt() {
        // Arrange
        String code = service.createCode(42L);

        // Act
        Long first = service.consumeCode(code);
        Long second = service.consumeCode(code);

        // Assert: single use — second presentation is rejected (replay protection)
        assertThat(first).isEqualTo(42L);
        assertThat(second).isNull();
    }

    @Test
    void consumeCode_whenCodeHasLowercaseAndWhitespace_normalizesBeforeLookup() {
        // Arrange
        String code = service.createCode(7L);

        // Act: bot messages arrive with arbitrary casing/whitespace
        Long userId = service.consumeCode(" " + code.toLowerCase());

        // Assert
        assertThat(userId).isEqualTo(7L);
    }

    @Test
    void consumeCode_whenCodeUnknownOrBlank_returnsNull() {
        // Act / Assert: boundary values — null, empty, blank, unknown
        assertThat(service.consumeCode(null)).isNull();
        assertThat(service.consumeCode("")).isNull();
        assertThat(service.consumeCode("   ")).isNull();
        assertThat(service.consumeCode("DEADBEEFDEADBEEF")).isNull();
    }

    @Test
    void consumeCode_whenCodeExpired_returnsNullAndRemovesIt() throws Exception {
        // Arrange: create a code, then backdate its expiry into the past
        String code = service.createCode(9L);
        setExpiry(code, Instant.now().minusSeconds(1));

        // Act
        Long userId = service.consumeCode(code);

        // Assert: expired code is rejected and removed
        assertThat(userId).isNull();
        assertThat(pendingBindings()).doesNotContainKey(code);
    }

    @Test
    void createCode_whenExpiredCodesExist_evictsThemBeforeCreatingNew() throws Exception {
        // Arrange: seed an expired binding directly
        String stale = service.createCode(1L);
        setExpiry(stale, Instant.now().minusSeconds(600));

        // Act
        service.createCode(2L);

        // Assert: the expired entry was evicted during createCode
        assertThat(pendingBindings()).doesNotContainKey(stale);
    }
}
