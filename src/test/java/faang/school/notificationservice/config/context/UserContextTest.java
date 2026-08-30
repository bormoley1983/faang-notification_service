package faang.school.notificationservice.config.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextTest {

    private final UserContext userContext = new UserContext();

    @AfterEach
    void tearDown() {
        userContext.clear();
    }

    @Test
    void getUserId_whenNeverSet_returnsZero() {
        // Act / Assert: boundary — no value in the ThreadLocal
        assertThat(userContext.getUserId()).isZero();
    }

    @Test
    void getUserId_whenSet_returnsStoredValue() {
        // Arrange
        userContext.setUserId(42L);

        // Act / Assert
        assertThat(userContext.getUserId()).isEqualTo(42L);
    }

    @Test
    void clear_whenCalled_removesStoredValue() {
        // Arrange
        userContext.setUserId(42L);

        // Act
        userContext.clear();

        // Assert: back to the default, no ThreadLocal leak
        assertThat(userContext.getUserId()).isZero();
    }
}
