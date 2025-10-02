package com.example.ecommerce.models.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotNull(message = "Subcategory ID is required")
    private Long subcategoryId;

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotBlank(message = "Slug is required")
    private String slug;

    private String sku;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Discount price must be greater than or equal to 0")
    private BigDecimal discountPrice;

    @Min(value = 0, message = "Stock quantity must be greater than or equal to 0")
    private Integer stockQuantity;

    private BigDecimal weight;

    private String dimensions;

    private Boolean isActive;
}
