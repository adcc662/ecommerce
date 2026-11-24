package com.example.ecommerce.models.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static OrderRequest withLines(int lines) {
        List<OrderItemRequest> items = LongStream.rangeClosed(1, lines)
                .mapToObj(id -> OrderItemRequest.builder().productId(id).quantity(1).build())
                .toList();
        return OrderRequest.builder().shippingAddressId(1L).billingAddressId(1L).items(items).build();
    }

    @Test
    void acceptsUpToTheMaximumNumberOfLines() {
        assertThat(validator.validate(withLines(OrderRequest.MAX_ITEMS))).isEmpty();
    }

    @Test
    void rejectsMoreLinesThanTheMaximum() {
        Set<ConstraintViolation<OrderRequest>> violations = validator.validate(withLines(OrderRequest.MAX_ITEMS + 1));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath()).hasToString("items");
            assertThat(violation.getMessage()).isEqualTo("An order can have at most 100 different products");
        });
    }

    @Test
    void stillRejectsAnEmptyOrder() {
        assertThat(validator.validate(withLines(0)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Order items cannot be empty");
    }
}
