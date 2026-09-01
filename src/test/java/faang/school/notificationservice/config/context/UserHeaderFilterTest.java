package faang.school.notificationservice.config.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserHeaderFilterTest {

    @Mock
    private FilterChain chain;

    private final UserContext userContext = new UserContext();

    private final UserHeaderFilter filter = new UserHeaderFilter(userContext);

    @AfterEach
    void tearDown() {
        userContext.clear();
    }

    @Test
    void doFilter_whenHeaderPresent_setsContextForChainAndClearsAfterwards() throws Exception {
        // Arrange: capture the context state while the chain runs
        final long[] seenInChain = new long[1];
        doAnswer(invocation -> {
            seenInChain[0] = userContext.getUserId();
            return null;
        }).when(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-user-id", "42");

        // Act
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // Assert: context visible inside the chain, cleared afterwards (no ThreadLocal leak)
        assertThat(seenInChain[0]).isEqualTo(42L);
        assertThat(userContext.getUserId()).isZero();
    }

    @Test
    void doFilter_whenHeaderMissing_throwsAndDoesNotInvokeChain() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();

        // Act / Assert: typed failure before the chain runs
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("x-user-id");
        verify(chain, org.mockito.Mockito.never())
                .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doFilter_whenActuatorRequestHasNoHeader_invokesChain() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/actuator/health/readiness");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertThat(userContext.getUserId()).isZero();
    }

    @Test
    void doFilter_whenChainFails_stillClearsContext() throws Exception {
        // Arrange: downstream handler throws
        org.mockito.Mockito.doThrow(new ServletException("boom"))
                .when(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-user-id", "7");

        // Act / Assert: exception propagates, but the ThreadLocal is cleaned up in finally
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
                .isInstanceOf(ServletException.class);
        assertThat(userContext.getUserId()).isZero();
    }
}
