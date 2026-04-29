package syber.security.digitalsignature.auth.otp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "application.auth.otp")
public class OtpProperties {

    private int registrationTtlMinutes = 10;
    private int login2faTtlMinutes = 5;
    private int tempPasswordTtlMinutes = 10;
    private int codeLength = 6;
    private int maxAttempts = 5;
    private int resendCooldownSeconds = 60;
}
