package syber.security.digitalsignature.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "application.auth.mail")
public class MailProperties {

    private String from = "no-reply@digital-signature.local";
    private String appName = "Digital Signature";
    private boolean enabled = true;
}
