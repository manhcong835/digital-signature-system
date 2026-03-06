package syber.security.digitalsignature.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "application.file-storage")
public class FileStorageProperties {

    private String uploadDir = "./uploads";
    private long maxFileSize = 10_485_760L;
}
