package com.example.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Mail settings are required outside the dev profile; the context never sends email here.
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "app.mail.from=test@ecommerce.local",
        "app.password-reset.url=http://localhost:3000/reset-password"
})
class EcommerceApplicationTests {

    @Test
    void contextLoads() {
    }

}
