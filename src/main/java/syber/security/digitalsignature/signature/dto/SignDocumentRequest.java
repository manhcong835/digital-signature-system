package syber.security.digitalsignature.signature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SignDocumentRequest {

    @NotNull(message = "Key ID is required")
    private Long keyId;

    @NotBlank(message = "Password is required to decrypt the private key")
    private String password;

    private Integer signaturePage;
    private SignatureRect signatureRect;
    private String reason;
    private String location;

    // True (default): PAdES with visible widget at signatureRect. False: PAdES
    // embedded but no widget on the page (Adobe still reports as signed).
    private Boolean visible;

    @Data
    public static class SignatureRect {
        private Double x;
        private Double y;
        private Double w;
        private Double h;
    }
}
