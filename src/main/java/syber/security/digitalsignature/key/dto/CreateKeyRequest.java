package syber.security.digitalsignature.key.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateKeyRequest {

    @NotBlank(message = "Key name is required")
    @Size(max = 255, message = "Key name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Password is required to encrypt the private key")
    private String password;
}
