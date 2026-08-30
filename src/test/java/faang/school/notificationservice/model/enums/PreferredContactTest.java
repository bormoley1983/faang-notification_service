package faang.school.notificationservice.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferredContactTest {

    @ParameterizedTest
    @CsvSource({
            "EMAIL, EMAIL",
            "email, EMAIL",
            "Sms, SMS",
            "TELEGRAM, TELEGRAM"
    })
    void fromString_whenKnownNameCaseInsensitive_returnsMatchingConstant(String input, PreferredContact expected) {
        // Act / Assert
        assertThat(PreferredContact.fromString(input)).isEqualTo(expected);
    }

    @Test
    void fromString_whenUnknownName_throwsIllegalArgumentWithInput() {
        // Act / Assert: boundary — no matching constant
        assertThatThrownBy(() -> PreferredContact.fromString("Pigeon"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pigeon");
    }
}
