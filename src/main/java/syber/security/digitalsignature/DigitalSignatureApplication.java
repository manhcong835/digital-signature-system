package syber.security.digitalsignature;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DigitalSignatureApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalSignatureApplication.class, args);
    }
}
