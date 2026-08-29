package faang.school.notificationservice.service;

import faang.school.notificationservice.bot.TelegramBot;
import faang.school.notificationservice.exception.NotificationDeliveryException;
import faang.school.notificationservice.model.dto.UserDto;
import faang.school.notificationservice.model.enums.PreferredContact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@RequiredArgsConstructor
@Service
public class TelegramService implements NotificationService {

    private final TelegramBot telegramBot;

    @Override
    public void send(UserDto user, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(user.getTelegramChatId());
        sendMessage.setText(message);
        try {
            telegramBot.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to user with id = {}", user.getId(), e);
            throw new NotificationDeliveryException(
                    "Telegram delivery failed for user id " + user.getId(), e);
        }
    }

    @Override
    public PreferredContact getPreferredContact() {
        return PreferredContact.TELEGRAM;
    }
}
