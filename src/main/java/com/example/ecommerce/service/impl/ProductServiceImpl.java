package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.ProductRequest;
import com.example.ecommerce.models.dto.response.ProductResponse;
import com.example.ecommerce.models.entity.Product;
import com.example.ecommerce.models.entity.Subcategory;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.SubcategoryRepository;
import com.example.ecommerce.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    /** Upper bound for the non-paginated list endpoints. Use paginated=true for more. */
    public static final int MAX_LIST_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final Set<String> PATCHABLE_FIELDS = Set.of(
            "name", "description", "slug", "sku", "price", "discountPrice",
            "stockQuantity", "weight", "dimensions", "isActive", "subcategoryId");

    private final ProductRepository productRepository;
    private final SubcategoryRepository subcategoryRepository;

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        validateDiscount(request.getPrice(), request.getDiscountPrice());
        Subcategory subcategory = subcategoryRepository.findById(request.getSubcategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found"));

        Product product = Product.builder()
                .subcategory(subcategory)
                .name(request.getName())
                .description(request.getDescription())
                .slug(request.getSlug())
                .sku(request.getSku())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                .weight(request.getWeight())
                .dimensions(request.getDimensions())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        product = productRepository.save(product);
        return mapToResponse(product);
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        validateDiscount(request.getPrice(), request.getDiscountPrice());
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Subcategory subcategory = subcategoryRepository.findById(request.getSubcategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found"));

        product.setSubcategory(subcategory);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setSlug(request.getSlug());
        product.setSku(request.getSku());
        product.setPrice(request.getPrice());
        product.setDiscountPrice(request.getDiscountPrice());
        product.setStockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0);
        product.setWeight(request.getWeight());
        product.setDimensions(request.getDimensions());
        product.setIsActive(request.getIsActive() != null ? request.getIsActive() : product.getIsActive());

        product = productRepository.save(product);
        return mapToResponse(product);
    }

    /**
     * Applies a partial update. Every field is type- and range-checked before the
     * product is touched, so invalid input returns 400 and changes nothing.
     */
    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse patchProduct(Long id, Map<String, Object> updates) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        for (String key : updates.keySet()) {
            if (!PATCHABLE_FIELDS.contains(key)) {
                throw new IllegalArgumentException("Unknown or read-only field: " + key);
            }
        }

        String name = updates.containsKey("name") ? requiredText(updates, "name") : product.getName();
        String slug = updates.containsKey("slug") ? requiredText(updates, "slug") : product.getSlug();
        BigDecimal price = updates.containsKey("price") ? requiredDecimal(updates, "price") : product.getPrice();
        BigDecimal discountPrice = updates.containsKey("discountPrice")
                ? optionalDecimal(updates, "discountPrice") : product.getDiscountPrice();
        Integer stockQuantity = updates.containsKey("stockQuantity")
                ? requiredInteger(updates, "stockQuantity") : product.getStockQuantity();
        BigDecimal weight = updates.containsKey("weight") ? optionalDecimal(updates, "weight") : product.getWeight();
        Boolean isActive = updates.containsKey("isActive") ? requiredBoolean(updates, "isActive") : product.getIsActive();

        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity must be greater than or equal to 0");
        }
        if (weight != null && weight.signum() < 0) {
            throw new IllegalArgumentException("Weight must be greater than or equal to 0");
        }
        validateDiscount(price, discountPrice);

        Subcategory subcategory = product.getSubcategory();
        if (updates.containsKey("subcategoryId")) {
            Object value = updates.get("subcategoryId");
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("subcategoryId must be a number");
            }
            subcategory = subcategoryRepository.findById(number.longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found"));
        }

        product.setName(name);
        product.setSlug(slug);
        product.setPrice(price);
        product.setDiscountPrice(discountPrice);
        product.setStockQuantity(stockQuantity);
        product.setWeight(weight);
        product.setIsActive(isActive);
        product.setSubcategory(subcategory);
        if (updates.containsKey("description")) {
            product.setDescription(optionalText(updates, "description"));
        }
        if (updates.containsKey("sku")) {
            product.setSku(optionalText(updates, "sku"));
        }
        if (updates.containsKey("dimensions")) {
            product.setDimensions(optionalText(updates, "dimensions"));
        }

        product = productRepository.save(product);
        return mapToResponse(product);
    }

    @Override
    @Cacheable(value = "products", key = "'id:' + #id")
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return mapToResponse(product);
    }

    @Override
    @Cacheable(value = "products", key = "'slug:' + #slug")
    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return mapToResponse(product);
    }

    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll(firstPage()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Pages and searches are not cached: their keys depend on sort and keyword, and
    // caching them let one sort order be served for another and let any caller fill Redis.
    @Override
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Override
    public List<ProductResponse> getActiveProducts() {
        return productRepository.findByIsActiveTrue(firstPage()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ProductResponse> getActiveProducts(Pageable pageable) {
        return productRepository.findByIsActiveTrue(pageable)
                .map(this::mapToResponse);
    }

    @Override
    public List<ProductResponse> searchProductsByName(String keyword) {
        return productRepository.searchByName(validKeyword(keyword), firstPage()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ProductResponse> searchProductsByName(String keyword, Pageable pageable) {
        return productRepository.searchByName(validKeyword(keyword), pageable)
                .map(this::mapToResponse);
    }

    /**
     * Deactivates the product. Hard deletes would cascade into order items and
     * erase order history.
     */
    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        product.setIsActive(false);
        productRepository.save(product);
    }

    private static Pageable firstPage() {
        return PageRequest.of(0, MAX_LIST_SIZE, Sort.by("id"));
    }

    private static String validKeyword(String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("Keyword must be 1 to " + MAX_KEYWORD_LENGTH + " characters");
        }
        return keyword.trim();
    }

    private static void validateDiscount(BigDecimal price, BigDecimal discountPrice) {
        if (discountPrice == null) {
            return;
        }
        if (discountPrice.signum() < 0) {
            throw new IllegalArgumentException("Discount price must be greater than or equal to 0");
        }
        if (price != null && discountPrice.compareTo(price) >= 0) {
            throw new IllegalArgumentException("Discount price must be lower than price");
        }
    }

    private static String requiredText(Map<String, Object> updates, String field) {
        String value = optionalText(updates, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String optionalText(Map<String, Object> updates, String field) {
        Object value = updates.get(field);
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return (String) value;
    }

    private static BigDecimal requiredDecimal(Map<String, Object> updates, String field) {
        BigDecimal value = optionalDecimal(updates, field);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static BigDecimal optionalDecimal(Map<String, Object> updates, String field) {
        Object value = updates.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number) && !(value instanceof String)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    private static Integer requiredInteger(Map<String, Object> updates, String field) {
        Object value = updates.get(field);
        if (!(value instanceof Integer integer)) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return integer;
    }

    private static Boolean requiredBoolean(Map<String, Object> updates, String field) {
        Object value = updates.get(field);
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException(field + " must be true or false");
        }
        return bool;
    }

    private ProductResponse mapToResponse(Product product) {
        List<String> imageUrls = product.getImages().stream()
                .map(img -> img.getImageUrl())
                .collect(Collectors.toList());

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .slug(product.getSlug())
                .sku(product.getSku())
                .price(product.getPrice())
                .discountPrice(product.getDiscountPrice())
                .stockQuantity(product.getStockQuantity())
                .weight(product.getWeight())
                .dimensions(product.getDimensions())
                .isActive(product.getIsActive())
                .subcategoryName(product.getSubcategory().getName())
                .categoryName(product.getSubcategory().getCategory().getName())
                .imageUrls(imageUrls)
                .createdAt(product.getCreatedAt())
                .build();
    }
}
