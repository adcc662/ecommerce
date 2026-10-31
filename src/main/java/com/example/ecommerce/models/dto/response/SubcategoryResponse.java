package com.example.ecommerce.models.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubcategoryResponse {
    private Long id;
    private Long categoryId;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
}
