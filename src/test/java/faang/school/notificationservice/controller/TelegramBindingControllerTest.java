package faang.school.notificationservice.controller;

import faang.school.notificationservice.config.context.UserContext;
import faang.school.notificationservice.service.TelegramBindingCodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramBindingControllerTest {

    @Mock
    private TelegramBindingCodeService bindingCodeService;

    @Mock
    private UserContext userContext;

    @InjectMocks
    private TelegramBindingController controller;

    @AfterEach
    void tearDown() {
        userContext.clear();
    }

    @Test
    void createBindingCode_whenAuthenticated_issuesCodeForCallerOnly() {
        // Arrange: the code must be bound to the authenticated caller, not a client-supplied id
        when(userContext.getUserId()).thenReturn(42L);
        when(bindingCodeService.createCode(42L)).thenReturn("ABCDEF0123456789");

        // Act
        ResponseEntity<Map<String, String>> response = controller.createBindingCode();

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("code", "ABCDEF0123456789");
        verify(bindingCodeService).createCode(42L);
    }
}
