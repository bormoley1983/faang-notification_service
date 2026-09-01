package faang.school.notificationservice.config.sms;

import com.vonage.client.VonageClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "notification.channels.sms", name = "enabled", havingValue = "true")
@Getter
public class VonageConfig {
    @Value("${vonage.api.key}")
    private String key;
    @Value("${vonage.api.secret}")
    private String secret;
    @Value("${vonage.api.from}")
    private String from;

    @Bean
    public VonageClient getVonageClient() {
        return VonageClient.builder()
                .apiKey(key)
                .apiSecret(secret)
                .build();
    }
}
