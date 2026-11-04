# E-Commerce REST API

A comprehensive e-commerce REST API built with Spring Boot 4.1, featuring JWT authentication, Redis caching, and a fully normalized PostgreSQL database.

## Features

- **JWT Authentication** - Secure token-based authentication with 24-hour expiration
- **Redis Caching** - Automatic caching for product queries with 10-minute TTL
- **Pagination Support** - Configurable pagination for all product listings
- **PATCH Endpoints** - Partial updates for products and order status
- **Flyway Migrations** - Version-controlled database schema management
- **Login rate limiting** - Failed logins limited per email and IP
- **Soft delete** - Deleting a product keeps order history
- **Docker Compose** - Easy setup for PostgreSQL and Redis
- **3NF Database** - 13 normalized tables with optimized indexes

## Tech Stack

- **Java 25** (LTS)
- **Spring Boot 4.1.2**
- **PostgreSQL** - Primary database
- **Redis** - Caching layer
- **Flyway** - Database migrations
- **JWT** - Authentication
- **Lombok** - Boilerplate reduction
- **Gradle 9.8** - Build tool (wrapper included)

## Quick Start

### Prerequisites

- Java 25 (Gradle downloads it automatically via the toolchain resolver if missing)
- Docker and Docker Compose
- Gradle (or use included wrapper)

### 1. Configure the environment

```bash
cp .env.example .env   # then fill in POSTGRES_USER, POSTGRES_PASSWORD and JWT_SECRET
```

### 2. Run the Application

```bash
./gradlew bootRun
```

`bootRun` starts PostgreSQL and Redis from `compose.yaml` automatically and uses the `dev` profile,
which provides a dev-only JWT secret so you do not need to export `JWT_SECRET` locally.
The API is available at `http://localhost:8080`.

To create an admin on first start, set `ADMIN_EMAIL` and `ADMIN_PASSWORD` (12+ characters).
An existing account with that email is never promoted.

### 3. Run as a container

```bash
docker compose --profile app up -d --build
```

This builds the image and runs the app with PostgreSQL and Redis. The container uses the default
(production) profile, so `JWT_SECRET` must be set in `.env`; it refuses to start without it.
Health: `GET /actuator/health/readiness` (also used by the Docker `HEALTHCHECK`).

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

### Access rules

| Who | Endpoints |
|---|---|
| Anyone | `POST /api/auth/**`, `GET /api/products/{id}`, `GET /api/products/slug/{slug}`, `GET /api/products/active`, `GET /api/products/search`, `GET /api/categories/**` |
| Logged-in users | cart, orders, addresses, payments for their own orders, `GET /api/payment-methods`, `POST /api/auth/logout-all` |
| `ADMIN` | `GET /api/products` (all, including inactive), product create/update/delete, category creation, order and payment status updates |

Failed logins are limited to 5 per email and IP (20 per email) every 15 minutes; further attempts return `429` with a `Retry-After` header.

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

#### Log Out Everywhere
```http
POST /api/auth/logout-all
Authorization: Bearer {token}
```

Returns `204` and invalidates every token issued to the user so far, including the one used for this call. Log in again to get a new token.

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

Without `paginated=true`, list endpoints return at most 100 items.

Query Parameters:
- `paginated` - Enable pagination (true/false)
- `page` - Page number (default: 0)
- `size` - Items per page (default: 10, max: 100)
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

Values are type-checked: `stockQuantity` must be an integer, `isActive` a boolean, prices numbers.
`discountPrice` must be lower than `price`. Unknown fields return `400`.

#### Delete Product
```http
DELETE /api/products/{id}
Authorization: Bearer {token}
```

Deactivates the product (it disappears from public reads). Order history is kept.

### Categories

```http
GET  /api/categories                      # active categories with their subcategories
GET  /api/categories/{id}/subcategories
POST /api/categories                      # ADMIN
POST /api/categories/{id}/subcategories   # ADMIN
Content-Type: application/json

{ "name": "Laptops", "slug": "laptops", "description": "optional" }
```

Slugs use lowercase letters, digits and hyphens.

### Addresses

Orders need a shipping and a billing address that belong to the user.

```http
GET    /api/addresses
POST   /api/addresses
PUT    /api/addresses/{id}
DELETE /api/addresses/{id}     # 409 if an order uses it
Authorization: Bearer {token}
Content-Type: application/json

{
  "addressType": "BOTH",
  "streetAddress": "1 Main St",
  "city": "Springfield",
  "stateProvince": "IL",
  "postalCode": "62701",
  "country": "US",
  "isDefault": true
}
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

### Payments

```http
GET   /api/payment-methods
POST  /api/orders/{orderId}/payments        # body: { "paymentMethodId": 1 }
GET   /api/orders/{orderId}/payments
PATCH /api/payments/{id}/status?status=COMPLETED   # ADMIN: COMPLETED, FAILED, REFUNDED
Authorization: Bearer {token}
```

- Only `PENDING` orders can be paid, for their full total. An order has at most one active (`PENDING` or `COMPLETED`) payment; a second attempt returns `409`.
- Status changes: `PENDING` → `COMPLETED` or `FAILED`; `COMPLETED` → `REFUNDED`.
- A `COMPLETED` payment moves a `PENDING` order to `PROCESSING`. A payment of a `CANCELLED` or `REFUNDED` order cannot be completed (`409`).
- Refunding a payment is allowed only for a `DELIVERED` order, which then moves to `REFUNDED`; for earlier orders, cancel the order instead (`409` otherwise).
- Cancelling an order fails its `PENDING` payments and refunds its `COMPLETED` ones; refunding an order refunds its `COMPLETED` payment.
- Order and payment status changes lock the order row, so they cannot race each other.
- Payments are recorded by `ManualPaymentGateway` (transaction ids `MANUAL-...`). To use a provider, implement `PaymentGateway` and register it as a `@Primary` bean.

### Order totals

`totalAmount = subtotal + taxAmount + shippingCost`, where `taxAmount = subtotal × TAX_RATE` (rounded half-up to cents) and `shippingCost = SHIPPING_FEE`. Both default to `0`.

## Caching Strategy

Redis caching is automatically applied to product queries:

### Cached Operations
- `getProductById(Long id)` - key `id:{id}`
- `getProductBySlug(String slug)` - key `slug:{slug}`

Pages and searches are not cached, so every sort order and keyword is always correct.

### Cache Invalidation
The whole `products` cache is cleared on product create, update, patch and delete, and when an
order is placed or its status changes (stock changes). If Redis is unavailable, reads fall back to
the database.

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

### Environment variables

| Variable | Required | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | outside `bootRun` | PostgreSQL connection |
| `JWT_SECRET` | yes (except `dev` profile) | HS256 signing key, 32+ characters |
| `JWT_EXPIRATION_MS` | no (default 86400000) | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | no (default `http://localhost:3000`) | Allowed browser origins |
| `REDIS_PASSWORD` | no | Redis password (compose enables `requirepass` when set) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | no | Initial admin account |
| `TAX_RATE` | no (default 0) | Tax as a fraction of the subtotal, `0`–`1` (e.g. `0.0825`) |
| `SHIPPING_FEE` | no (default 0) | Flat shipping fee per order, `>= 0` |

Registered users default to the `USER` role. Behind a reverse proxy, `X-Forwarded-*` headers are
trusted only from private-network addresses (`server.forward-headers-strategy=native`).

### Docker Compose Services

- `postgres` (16) and `redis` bound to `127.0.0.1`, with healthchecks
- `app` (profile `app`): the containerized API on `127.0.0.1:8080`

## Design Notes

- Services are Spring-managed singletons injected through constructors.
- Checkout locks products in id order (`SELECT ... FOR UPDATE`) and decrements stock in the same transaction; cancelling an order restores it.
- Products are soft-deleted to preserve order history.
- Tokens carry the user's token version; "log out everywhere" increments it, so older tokens stop working.
- Payment creation locks the order row, and a partial unique index guarantees one active payment per order.

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
  "message": "Product not found"
}
```

| Status | When |
|---|---|
| 400 | Validation errors (with an `errors` map), malformed JSON, bad parameters |
| 401 | Missing, invalid or expired token; wrong credentials |
| 403 | Authenticated but not allowed |
| 404 | Resource not found (or not owned by the user) |
| 409 | Duplicate email, slug or SKU; deleting data still in use; paying an order that is not pending or already has an active payment |
| 429 | Too many failed logins |

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
