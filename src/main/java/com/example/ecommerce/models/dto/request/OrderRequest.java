package com.example.ecommerce.models.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    /** Each line locks one product row during checkout, so the number of lines is capped. */
    public static final int MAX_ITEMS = 100;

    @NotNull(message = "Shipping address ID is required")
    private Long shippingAddressId;

    @NotNull(message = "Billing address ID is required")
    private Long billingAddressId;

    @NotEmpty(message = "Order items cannot be empty")
    @Size(max = MAX_ITEMS, message = "An order can have at most {max} different products")
    @Valid
    private List<OrderItemRequest> items;
}
