package syber.security.digitalsignature.ca;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ca")
public class CaProperties {

    private String keystorePath = "./uploads/.ca/ca.p12";
    private String keystorePassword = "dev-ca-password-change-me";
    private String rootCn = "Digital Signature Demo CA";
    private int rootValidityYears = 10;
    private int certValidityYears = 2;
    private String crlDistributionUrl = "http://localhost:8080/api/v1/ca/crl";
}
