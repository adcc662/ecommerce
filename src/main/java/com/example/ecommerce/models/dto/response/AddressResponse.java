package com.example.ecommerce.models.dto.response;

import com.example.ecommerce.models.enums.AddressType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {
    private Long id;
    private AddressType addressType;
    private String streetAddress;
    private String apartment;
    private String city;
    private String stateProvince;
    private String postalCode;
    private String country;
    private Boolean isDefault;
    private LocalDateTime createdAt;
}
