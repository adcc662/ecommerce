# E-Commerce REST API

A comprehensive e-commerce REST API built with Spring Boot 3.5, featuring JWT authentication, Redis caching, and a fully normalized PostgreSQL database.

## Features

- **JWT Authentication** - Secure token-based authentication with 24-hour expiration
- **Redis Caching** - Automatic caching for product queries with 10-minute TTL
- **Pagination Support** - Configurable pagination for all product listings
- **PATCH Endpoints** - Partial updates for products and order status
- **Flyway Migrations** - Version-controlled database schema management
- **Singleton Pattern** - Thread-safe service implementations
- **Docker Compose** - Easy setup for PostgreSQL and Redis
- **3NF Database** - 13 normalized tables with optimized indexes

## Tech Stack

- **Java 21**
- **Spring Boot 3.5.3**
- **PostgreSQL** - Primary database
- **Redis** - Caching layer
- **Flyway** - Database migrations
- **JWT** - Authentication
- **Lombok** - Boilerplate reduction
- **Gradle** - Build tool

## Quick Start

### Prerequisites

- Java 21 or higher
- Docker and Docker Compose
- Gradle (or use included wrapper)

### 1. Start Docker Services

```bash
docker-compose up -d
```

This starts:
- PostgreSQL on port 5432
- Redis on port 6379

### 2. Run the Application

```bash
./gradlew bootRun
```

The API will be available at `http://localhost:8080`

## Project Structure

```
src/main/java/com/example/ecommerce/
├── config/              # Configuration classes (Security, Redis)
├── controller/          # REST controllers
├── exception/           # Custom exceptions and global handler
├── models/
│   ├── dto/            # DTOs for request/response
│   ├── entity/         # JPA entities
│   └── enums/          # Enums (OrderStatus, PaymentStatus, etc.)
├── repository/         # Spring Data JPA repositories
├── security/           # JWT utilities and filters
└── service/            # Business logic layer
    └── impl/           # Service implementations
```

## API Documentation

### Authentication

#### Register User
```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "1234567890"
}
```

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe"
}
```

### Products

#### Create Product
```http
POST /api/products
Authorization: Bearer {token}
Content-Type: application/json

{
  "subcategoryId": 1,
  "name": "Laptop Dell XPS 13",
  "description": "High-end laptop",
  "slug": "laptop-dell-xps-13",
  "sku": "DELL-XPS13-001",
  "price": 1299.99,
  "discountPrice": 1199.99,
  "stockQuantity": 50,
  "weight": 1.2,
  "dimensions": "30x20x1.5cm",
  "isActive": true
}
```

#### Get All Products
```http
# Without pagination
GET /api/products

# With pagination
GET /api/products?paginated=true&page=0&size=10&sortBy=price&sortDir=ASC
```

Query Parameters:
- `paginated` - Enable pagination (true/false)
- `page` - Page number (default: 0)
- `size` - Items per page (default: 10)
- `sortBy` - Sort field (default: id)
- `sortDir` - Sort direction: ASC or DESC (default: ASC)

#### Get Product by ID
```http
GET /api/products/{id}
```

#### Get Product by Slug
```http
GET /api/products/slug/{slug}
```

#### Get Active Products
```http
GET /api/products/active?paginated=true&page=0&size=20
```

#### Search Products
```http
GET /api/products/search?keyword=laptop&paginated=true
```

#### Update Product (PUT)
```http
PUT /api/products/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "subcategoryId": 1,
  "name": "Updated Product Name",
  "description": "Updated description",
  "slug": "updated-slug",
  "sku": "SKU-001",
  "price": 1399.99,
  "stockQuantity": 45
}
```

#### Partial Update (PATCH)
```http
PATCH /api/products/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "price": 1099.99,
  "stockQuantity": 40
}
```

Updatable fields: `name`, `description`, `slug`, `sku`, `price`, `discountPrice`, `stockQuantity`, `weight`, `dimensions`, `isActive`, `subcategoryId`

#### Delete Product
```http
DELETE /api/products/{id}
Authorization: Bearer {token}
```

### Shopping Cart

#### Add to Cart
```http
POST /api/cart/add?productId=1&quantity=2
Authorization: Bearer {token}
```

#### Update Quantity
```http
PUT /api/cart/update?productId=1&quantity=5
Authorization: Bearer {token}
```

#### Get Cart
```http
GET /api/cart
Authorization: Bearer {token}
```

#### Remove from Cart
```http
DELETE /api/cart/remove?productId=1
Authorization: Bearer {token}
```

#### Clear Cart
```http
DELETE /api/cart/clear
Authorization: Bearer {token}
```

### Orders

#### Create Order
```http
POST /api/orders
Authorization: Bearer {token}
Content-Type: application/json

{
  "shippingAddressId": 1,
  "billingAddressId": 1,
  "items": [
    {
      "productId": 1,
      "quantity": 2
    },
    {
      "productId": 2,
      "quantity": 1
    }
  ]
}
```

#### Get My Orders
```http
GET /api/orders/my-orders
Authorization: Bearer {token}
```

#### Get Order by ID
```http
GET /api/orders/{id}
Authorization: Bearer {token}
```

#### Update Order Status
```http
PATCH /api/orders/{id}/status?status=PROCESSING
Authorization: Bearer {token}
```

Valid statuses: `PENDING`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `REFUNDED`

## Caching Strategy

Redis caching is automatically applied to product queries:

### Cached Operations
- `getProductById(Long id)` - Cached by product ID
- `getProductBySlug(String slug)` - Cached by slug
- `getAllProducts(Pageable pageable)` - Cached by page parameters
- `getActiveProducts(Pageable pageable)` - Cached by page parameters
- `searchProductsByName(String keyword, Pageable pageable)` - Cached by keyword and page

### Cache Invalidation
Cache is automatically cleared on:
- `patchProduct()` - Clears all product cache entries
- `deleteProduct()` - Clears all product cache entries

**Cache TTL:** 10 minutes

## Database Schema

The database includes 13 normalized tables:

- `users` - User accounts
- `addresses` - Shipping/billing addresses
- `categories` - Product categories
- `subcategories` - Product subcategories
- `products` - Product information
- `product_images` - Product image URLs
- `favorites` - User wishlists
- `shopping_carts` - Shopping cart items
- `orders` - Order headers
- `order_items` - Order line items
- `payment_methods` - Saved payment methods
- `payments` - Payment transactions
- `product_reviews` - Product ratings and reviews

All tables include:
- Optimized indexes
- Foreign key constraints
- Automatic `created_at` and `updated_at` timestamps
- Triggers for `updated_at` fields

## Configuration

### application.properties

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/ecommerce
spring.datasource.username=myuser
spring.datasource.password=secret

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# JWT
jwt.secret=${JWT_SECRET}
jwt.expiration=${JWT_EXPIRATION_MS:86400000}

# Server
server.port=8080
```

Set `JWT_SECRET` in your local environment to a strong HS256-compatible secret before running the application. Registered users default to the `USER` role; product mutations and order status updates require `ADMIN`.

### Docker Compose Services

```yaml
services:
  postgres:
    image: postgres:latest
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: ecommerce
      POSTGRES_USER: myuser
      POSTGRES_PASSWORD: secret

  redis:
    image: redis:alpine
    ports:
      - "6379:6379"
```

## Design Patterns

### Singleton Pattern
Service implementations use thread-safe double-checked locking:

```java
private static volatile ProductServiceImpl instance;

public static ProductServiceImpl getInstance(
    ProductRepository productRepository,
    SubcategoryRepository subcategoryRepository) {
    if (instance == null) {
        synchronized (ProductServiceImpl.class) {
            if (instance == null) {
                instance = new ProductServiceImpl(
                    productRepository,
                    subcategoryRepository
                );
            }
        }
    }
    return instance;
}
```

## Development

### Build the Project
```bash
./gradlew build
```

### Run Tests
```bash
./gradlew test
```

### Database Migrations
Flyway migrations are located in `src/main/resources/db/migration/`

Migrations run automatically on application startup.

## Monitoring

### View Docker Logs
```bash
docker-compose logs -f
```

### Connect to PostgreSQL
```bash
docker exec -it <postgres-container> psql -U myuser -d ecommerce
```

### Connect to Redis CLI
```bash
docker exec -it <redis-container> redis-cli
```

### Monitor Cache
```bash
# Inside Redis CLI
KEYS *
GET products::<key>
TTL products::<key>
```

## Stop Services

```bash
# Stop services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

## API Response Format

### Success Response
```json
{
  "id": 1,
  "name": "Product Name",
  "price": 99.99,
  "createdAt": "2025-01-15T10:30:00"
}
```

### Paginated Response
```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalPages": 5,
  "totalElements": 50,
  "size": 10,
  "number": 0
}
```

### Error Response
```json
{
  "timestamp": "2025-01-15T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found",
  "path": "/api/products/999"
}
```

## License

This project is licensed under the MIT License.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Support

For issues and questions, please open an issue on GitHub.
