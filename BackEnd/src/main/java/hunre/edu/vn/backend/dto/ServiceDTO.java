package hunre.edu.vn.backend.dto;

import hunre.edu.vn.backend.entity.Service;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDTO{
    private String name;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GetServiceDTO {
        private Long id;
        private String name;
        private String image;
        private BigDecimal price;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveServiceDTO {
        private Long id;

        @NotBlank(message = "Tên dịch vụ không được trống")
        private String name;

        private String image;

        @NotBlank(message = "Giá dịch vụ không được trống")
        private BigDecimal price;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // Static method to convert Entity to DTO
    public static GetServiceDTO fromEntity(Service service) {
        if (service == null) return null;

        return GetServiceDTO.builder()
                .id(service.getId())
                .name(service.getName())
                .image(service.getImage())
                .price(service.getPrice())
                .description(service.getDescription())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }
}