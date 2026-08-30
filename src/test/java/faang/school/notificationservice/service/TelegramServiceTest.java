package faang.school.notificationservice.service;

import faang.school.notificationservice.bot.TelegramBot;
import faang.school.notificationservice.exception.NotificationDeliveryException;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramServiceTest {

    @Mock
    private TelegramBot telegramBot;

    @InjectMocks
    private TelegramService telegramService;

    private UserDto user() {
        UserDto dto = new UserDto();
        dto.setId(7L);
        dto.setTelegramChatId("12345");
        return dto;
    }

    @Test
    void send_whenBotSucceeds_deliversMessageToBoundChat() throws TelegramApiException {
        // Arrange
        when(telegramBot.execute(any(SendMessage.class))).thenReturn(null);

        // Act
        telegramService.send(user(), "hello");

        // Assert: chat id and text preserved on the outgoing message
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramBot).execute(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getChatId()).isEqualTo("12345");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getText()).isEqualTo("hello");
    }

    @Test
    void send_whenBotFails_throwsDeliveryExceptionWithoutLeakingToken() throws TelegramApiException {
        // Arrange: provider error (e.g. 401 Unauthorized) must not leak the bot token
        when(telegramBot.execute(any(SendMessage.class)))
                .thenThrow(new TelegramApiException("401 Unauthorized"));

        // Act / Assert
        assertThatThrownBy(() -> telegramService.send(user(), "hello"))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessage("Telegram delivery failed for user id 7")
                .hasRootCauseMessage("401 Unauthorized");
    }

    @Test
    void getPreferredContact_whenQueried_returnsTelegram() {
        // Act / Assert
        org.assertj.core.api.Assertions.assertThat(telegramService.getPreferredContact())
                .isEqualTo(PreferredContact.TELEGRAM);
    }
}
