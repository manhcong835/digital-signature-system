package syber.security.digitalsignature.signature.dto;

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
public class PadesVerifyResponse {

    private int signatureCount;
    private boolean allSignaturesValid;
    private List<Item> signatures;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String signerCn;
        private String issuerCn;
        private LocalDateTime signedAt;
        private boolean byteRangeCoversFile;
        private boolean incrementalUpdateValid;
        private boolean cryptoValid;
        private boolean certChainValid;
        private boolean certTrustedByLocalCa;
        private LocalDateTime certNotBefore;
        private LocalDateTime certNotAfter;
        private String error;
    }
}
