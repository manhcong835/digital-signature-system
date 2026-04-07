package syber.security.digitalsignature.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import syber.security.digitalsignature.document.entity.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    private Long id;
    private String originalName;
    private String mimeType;
    private Long size;
    private String sha512Hash;
    private LocalDateTime createdAt;
    private String status;

    public static DocumentResponse from(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .originalName(document.getOriginalName())
                .mimeType(document.getMimeType())
                .size(document.getSize())
                .sha512Hash(document.getSha512Hash())
                .createdAt(document.getCreatedAt())
                .status(document.getStatus())
                .build();
    }
}
