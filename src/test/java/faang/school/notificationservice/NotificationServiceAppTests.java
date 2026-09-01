package faang.school.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;


class NotificationServiceAppTests {
    @Test
    void applicationYamlDefinesKafkaConsumerPropertiesUnderSpringNamespace() throws IOException {
        var environment = new StandardEnvironment();
        var propertySources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        propertySources.forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("spring.kafka.bootstrap-servers"))
                .isEqualTo("localhost:9092");
        assertThat(environment.getProperty("spring.kafka.consumer.group-id"))
                .isEqualTo("notification-group");
        assertThat(environment.getProperty("spring.kafka.topics.comment-topic.name"))
                .isEqualTo("notification_comment_topic");
        assertThat(environment.getProperty("spring.kafka.topics.like-topic.name"))
                .isEqualTo("notification_like_topic");
        assertThat(environment.getProperty("spring.kafka.topics.event-start-topic.name"))
                .isEqualTo("event_start_topic");
        assertThat(environment.containsProperty("notification.kafka.bootstrap-servers")).isFalse();
    }
}
