package com.example.ecommerce.controller;

import com.example.ecommerce.config.SecurityConfig;
import com.example.ecommerce.security.JwtAuthenticationFilter;
import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.service.AddressService;
import com.example.ecommerce.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CategoryController.class, AddressController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef",
        "jwt.expiration=60000",
        "app.cors.allowed-origins=http://localhost:3000"
})
class CatalogAndAddressSecurityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private AddressService addressService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(
                User.withUsername("user@example.com").password("x").roles("USER").build());
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(
                User.withUsername("admin@example.com").password("x").roles("ADMIN").build());
    }

    private String token(String email) {
        return "Bearer " + jwtUtil.generateToken(email);
    }

    private static final String CATEGORY = "{\"name\":\"Electronics\",\"slug\":\"electronics\"}";

    @Test
    void categoriesArePublic() throws Exception {
        when(categoryService.getActiveCategories()).thenReturn(List.of());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/categories/1/subcategories")).andExpect(status().isOk());
    }

    @Test
    void onlyAdminsCreateCategories() throws Exception {
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(CATEGORY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/categories").header("Authorization", token("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(CATEGORY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/categories/1/subcategories").header("Authorization", token("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(CATEGORY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/categories").header("Authorization", token("admin@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(CATEGORY))
                .andExpect(status().isCreated());
    }

    @Test
    void categorySlugIsValidated() throws Exception {
        mvc.perform(post("/api/categories").header("Authorization", token("admin@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"E\",\"slug\":\"Bad Slug\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slug").exists());
    }

    @Test
    void addressesRequireLogin() throws Exception {
        mvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/addresses").header("Authorization", token("user@example.com")))
                .andExpect(status().isOk());
    }

    @Test
    void addressBodyIsValidated() throws Exception {
        mvc.perform(post("/api/addresses").header("Authorization", token("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.streetAddress").exists());
    }
}
