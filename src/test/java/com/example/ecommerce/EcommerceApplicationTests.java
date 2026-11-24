package com.example.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the whole application against the Postgres and Redis from compose.yaml, which Spring Boot's
 * Docker Compose support starts (if they are not running yet) and connects to, so plain
 * {@code ./gradlew test} works without exporting credentials. Requires Docker.
 * Mail settings are required outside the dev profile; the context never sends email here.
 */
@SpringBootTest(properties = {
        "spring.docker.compose.skip.in-tests=false",
        "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef",
        "spring.mail.host=localhost",
        "app.mail.from=test@ecommerce.local",
        "app.password-reset.url=http://localhost:3000/reset-password"
})
class EcommerceApplicationTests {

    @Test
    void contextLoads() {
    }

}
