package syber.security.digitalsignature.signature.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerifyResponse {

    private boolean valid;
    private String status;
    private String message;
    private String algorithm;
    private String documentName;
    private String expectedHash;
    private String computedHash;
    private Boolean hashMatches;
    private Boolean signatureValid;
    private String signerEmail;
    private String signerName;
    private String keyName;
    private LocalDateTime signedAt;
    private LocalDateTime verifiedAt;

    private List<VerifyResponse> signatures;
}
