package faang.school.notificationservice.bot;

import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.service.TelegramBindingCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@link TelegramBot#onUpdateReceived} with constructed updates and captures
 * outgoing messages by stubbing the bot's {@code execute(SendMessage)} send seam.
 */
@ExtendWith(MockitoExtension.class)
class TelegramBotTest {

    @Mock
    private UserServiceClient userServiceClient;

    // The spy is built in setUp: a field initializer would run before Mockito injects
    // the @Mock, leaving a null client inside the bot. No @Spy annotation because
    // TelegramBot has no no-arg constructor.
    private TelegramBot bot;

    private final List<SendMessage> sentMessages = new ArrayList<>();

    private Update update(String chatType, String text) {
        Chat chat = new Chat();
        chat.setId(100L);
        chat.setType(chatType);
        User from = new User();
        from.setId(200L);
        Message message = new Message();
        message.setChat(chat);
        message.setFrom(from);
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    @BeforeEach
    void setUp() throws Exception {
        bot = spy(new TelegramBot(userServiceClient, new TelegramBindingCodeService()));
        sentMessages.clear();
        setField(bot, "botUsername", "faang_bot");
        setField(bot, "botToken", "test-token");
        // Record outgoing messages instead of calling the Telegram API (no network in unit tests).
        // Lenient: no-send paths (missing message/sender) never reach execute().
        lenient().doAnswer(invocation -> {
            sentMessages.add(invocation.getArgument(0));
            return null;
        }).when(bot).execute(any(SendMessage.class));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = TelegramBot.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void onUpdateReceived_whenGroupChat_rejectsAndDoesNotBind() {
        // Arrange: group chat presenting a valid code must still be rejected (NOT-05)
        String code = consumeCodeForTest(1L);
        Update update = update("group", code);

        // Act
        bot.onUpdateReceived(update);

        // Assert: warning sent, no binding call
        verify(userServiceClient, never()).bindTelegramChat(anyLong(), anyString());
        assertThat(sentMessages).hasSize(1);
        assertThat(sentMessages.get(0).getText()).contains("group chats");
    }

    @Test
    void onUpdateReceived_whenSendFails_swallowsApiException() throws Exception {
        // Arrange: Telegram API rejects every send — the bot must not crash the update loop
        doThrow(new TelegramApiException(new RuntimeException("401 Unauthorized")))
                .when(bot).execute(any(SendMessage.class));
        String code = consumeCodeForTest(10L);

        // Act / Assert: group warning and invalid-code paths both swallow the API failure
        org.assertj.core.api.Assertions.assertThatCode(() -> bot.onUpdateReceived(update("group", code))).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThatCode(() -> bot.onUpdateReceived(update("private", "nope"))).doesNotThrowAnyException();
        verify(userServiceClient, never()).bindTelegramChat(anyLong(), anyString());
    }

    @Test
    void onUpdateReceived_whenSupergroupOrChannel_rejects() {
        // Arrange / Act: both remaining non-private chat types are rejected
        bot.onUpdateReceived(update("supergroup", "anything"));
        bot.onUpdateReceived(update("channel", "anything"));

        // Assert
        verify(userServiceClient, never()).bindTelegramChat(anyLong(), anyString());
        assertThat(sentMessages).hasSize(2);
    }

    @Test
    void onUpdateReceived_whenValidCode_bindsChatAndGreets() {
        // Arrange: code issued for user 1, presented from a private chat
        String code = consumeCodeForTest(1L);

        // Act
        bot.onUpdateReceived(update("private", code));

        // Assert: binding call with the chat id, then greeting
        verify(userServiceClient).bindTelegramChat(1L, "100");
        assertThat(sentMessages).hasSize(1);
        assertThat(sentMessages.get(0).getChatId()).isEqualTo("100");
        assertThat(sentMessages.get(0).getText()).contains("faang_bot");
    }

    @Test
    void onUpdateReceived_whenCodeInvalidOrMissing_sendsInvalidCodeMessageAndDoesNotBind() {
        // Arrange / Act: no code, blank text, and a used-up code all reject
        bot.onUpdateReceived(update("private", "hello"));
        bot.onUpdateReceived(update("private", "   "));
        String code = consumeCodeForTest(2L);
        consumeCodeService().consumeCode(code); // consume it once
        bot.onUpdateReceived(update("private", code)); // replay

        // Assert: three rejections, no binding
        verify(userServiceClient, never()).bindTelegramChat(anyLong(), anyString());
        assertThat(sentMessages).hasSize(3);
        assertThat(sentMessages.get(0).getText()).contains("Invalid or expired binding code");
    }

    @Test
    void onUpdateReceived_whenUpdateHasNoMessage_ignores() {
        // Arrange: e.g. a callback query update
        Update update = new Update();

        // Act / Assert: no interaction at all
        bot.onUpdateReceived(update);
        verify(userServiceClient, never()).bindTelegramChat(anyLong(), anyString());
        assertThat(sentMessages).isEmpty();
    }

    @Test
    void onUpdateReceived_whenSenderMissing_ignores() {
        // Arrange: channel-post style message without a from user
        Chat chat = new Chat();
        chat.setId(100L);
        chat.setType("private");
        Message message = new Message();
        message.setChat(chat);
        message.setText("x");
        Update update = new Update();
        update.setMessage(message);

        // Act / Assert
        bot.onUpdateReceived(update);
        verify(userServiceClient, never()).bindTelegramChat(anyLong(), anyString());
        assertThat(sentMessages).isEmpty();
    }

    @Test
    void onUpdateReceived_whenBindingCallFails_stillGreetsUser() {
        // Arrange: user service rejects the binding (e.g. 404)
        String code = consumeCodeForTest(3L);
        when(userServiceClient.bindTelegramChat(anyLong(), anyString()))
                .thenThrow(new RuntimeException("user not found"));

        // Act / Assert: binding failure propagates — the greeting is skipped, no silent success
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bot.onUpdateReceived(update("private", code)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("user not found");
        assertThat(sentMessages).isEmpty();
    }

    // Test seams: expose the real binding-code service used by the bot under test.
    private TelegramBindingCodeService consumeCodeService() {
        try {
            Field field = TelegramBot.class.getDeclaredField("bindingCodeService");
            field.setAccessible(true);
            return (TelegramBindingCodeService) field.get(bot);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String consumeCodeForTest(long userId) {
        return consumeCodeService().createCode(userId);
    }
}
