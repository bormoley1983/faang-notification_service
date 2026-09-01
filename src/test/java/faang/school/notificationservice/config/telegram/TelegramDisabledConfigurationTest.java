package faang.school.notificationservice.config.telegram;

import faang.school.notificationservice.bot.TelegramBot;
import faang.school.notificationservice.config.email.MailSenderConfig;
import faang.school.notificationservice.config.sms.VonageConfig;
import faang.school.notificationservice.service.EmailService;
import faang.school.notificationservice.service.SmsService;
import faang.school.notificationservice.service.TelegramService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramDisabledConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    TelegramBotConfig.class,
                    TelegramBot.class,
                    TelegramService.class,
                    MailSenderConfig.class,
                    EmailService.class,
                    VonageConfig.class,
                    SmsService.class)
            .withPropertyValues(
                    "telegram.bot.enabled=false",
                    "notification.channels.email.enabled=false",
                    "notification.channels.sms.enabled=false");

    @Test
    void telegramBeansAreAbsentWhenIntegrationIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(TelegramBot.class);
            assertThat(context).doesNotHaveBean(TelegramService.class);
            assertThat(context).doesNotHaveBean("telegramBotsApi");
            assertThat(context).doesNotHaveBean(EmailService.class);
            assertThat(context).doesNotHaveBean(SmsService.class);
            assertThat(context).doesNotHaveBean("getJavaMailSender");
            assertThat(context).doesNotHaveBean("getVonageClient");
        });
    }
}
