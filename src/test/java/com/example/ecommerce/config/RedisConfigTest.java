package com.example.ecommerce.config;

import com.example.ecommerce.models.dto.response.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @SuppressWarnings("unchecked")
    private final RedisSerializer<Object> serializer = (RedisSerializer<Object>) new RedisConfig()
            .redisTemplate(mock(RedisConnectionFactory.class))
            .getValueSerializer();

    @Test
    void roundTripsCachedProductWithTypeInformation() {
        ProductResponse product = ProductResponse.builder()
                .id(1L)
                .name("Keyboard")
                .price(new BigDecimal("49.90"))
                .imageUrls(List.of("https://cdn.example.com/k.png"))
                .createdAt(LocalDateTime.of(2026, 10, 31, 12, 0))
                .build();

        byte[] bytes = serializer.serialize(product);

        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("\"@class\"");
        assertThat(serializer.deserialize(bytes)).isEqualTo(product);
    }

    @Test
    void rejectsTypesOutsideTheAllowlist() {
        byte[] payload = "{\"@class\":\"java.net.URL\",\"protocol\":\"http\",\"host\":\"evil.test\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(payload))
                .isInstanceOf(SerializationException.class);
    }
}
