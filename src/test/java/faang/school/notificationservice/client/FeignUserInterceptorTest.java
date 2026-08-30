package faang.school.notificationservice.client;

import faang.school.notificationservice.config.context.UserContext;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeignUserInterceptorTest {

    private final UserContext userContext = new UserContext();

    private final FeignUserInterceptor interceptor = new FeignUserInterceptor(userContext);

    @AfterEach
    void tearDown() {
        userContext.clear();
    }

    @Test
    void apply_whenUserIdSet_addsHeaderToOutboundRequest() {
        // Arrange
        userContext.setUserId(42L);
        RequestTemplate template = new RequestTemplate();

        // Act
        interceptor.apply(template);

        // Assert: the authenticated caller is forwarded to the user service
        assertThat(template.headers()).containsEntry("x-user-id", java.util.List.of("42"));
    }

    @Test
    void apply_whenNoUserIdSet_sendsZeroHeader() {
        // Arrange: no request context (e.g. internal call) → default 0
        RequestTemplate template = new RequestTemplate();

        // Act
        interceptor.apply(template);

        // Assert
        assertThat(template.headers()).containsEntry("x-user-id", java.util.List.of("0"));
    }
}
