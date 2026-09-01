package faang.school.notificationservice.bot;

import faang.school.notificationservice.client.UserServiceClient;
import faang.school.notificationservice.service.TelegramBindingCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(prefix = "telegram.bot", name = "enabled", havingValue = "true")
public class TelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    private final UserServiceClient userServiceClient;
    private final TelegramBindingCodeService bindingCodeService;

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) {
            return;
        }
        Message updateMessage = update.getMessage();
        Chat chat = updateMessage.getChat();

        // NOT-05: group/supergroup/channel chats are rejected immediately - no greeting, no binding.
        String chatType = chat.getType();
        if (chat.isGroupChat() || "supergroup".equals(chatType) || "channel".equals(chatType)) {
            log.warn("Attempt to use bot from group/channel chat, chat id = {}", chat.getId());
            sendGroupChatWarning(updateMessage.getChatId().toString());
            return;
        }

        User messageFrom = updateMessage.getFrom();
        if (messageFrom == null) {
            return;
        }

        String text = updateMessage.getText();
        // NOT-04: binding requires a valid single-use code issued by the authenticated
        // application for this specific user. Free-form username/chat pairs are rejected.
        Long boundUserId = bindingCodeService.consumeCode(text);
        if (boundUserId == null) {
            log.info("Telegram message without a valid binding code from chat id = {}",
                    updateMessage.getChatId());
            sendInvalidCodeMessage(updateMessage.getChatId().toString());
            return;
        }

        String telegramChatId = updateMessage.getChatId().toString();
        userServiceClient.bindTelegramChat(boundUserId, telegramChatId);
        log.info("Bound Telegram chat for user id = {}", boundUserId);
        sendHelloMessage(telegramChatId);
    }

    private void sendHelloMessage(String chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Hello nice to meet you with " + botUsername + "!");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("TelegramApiException was occurred while send warn to group chat", e);
        }
    }

    private void sendGroupChatWarning(String chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("This bot doesn't work with group chats");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send group chat warning to chat id = {}", chatId, e);
        }
    }

    private void sendInvalidCodeMessage(String chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Invalid or expired binding code. Open the application and copy a fresh code.");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send invalid-code message to chat id = {}", chatId, e);
        }
    }
}
