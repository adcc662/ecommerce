# E-Commerce API - Guía de Uso

## 🚀 Iniciar la Aplicación

### 1. Iniciar los servicios con Docker Compose
```bash
docker-compose up -d
```

Esto iniciará:
- PostgreSQL en el puerto 5432
- Redis en el puerto 6379

### 2. Ejecutar la aplicación
```bash
./gradlew bootRun
```

La aplicación estará disponible en `http://localhost:8080`

## 📋 Endpoints Principales

### Autenticación

#### Registrar Usuario
```bash
POST /api/auth/register
Content-Type: application/json

{
  "email": "usuario@ejemplo.com",
  "password": "password123",
  "firstName": "Juan",
  "lastName": "Pérez",
  "phone": "1234567890"
}
```

#### Login
```bash
POST /api/auth/login
Content-Type: application/json

{
  "email": "usuario@ejemplo.com",
  "password": "password123"
}
```

Respuesta:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "usuario@ejemplo.com",
  "firstName": "Juan",
  "lastName": "Pérez"
}
```

### Productos

#### Crear Producto (requiere autenticación)
```bash
POST /api/products
Authorization: Bearer {token}
Content-Type: application/json

{
  "subcategoryId": 1,
  "name": "Laptop Dell XPS 13",
  "description": "Laptop de alta gama",
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

#### Obtener Productos (sin paginación)
```bash
GET /api/products
```

#### Obtener Productos (con paginación)
```bash
GET /api/products?paginated=true&page=0&size=10&sortBy=price&sortDir=ASC
```

Parámetros de paginación:
- `paginated`: true/false (activa la paginación)
- `page`: número de página (default: 0)
- `size`: elementos por página (default: 10)
- `sortBy`: campo para ordenar (default: id)
- `sortDir`: ASC o DESC (default: ASC)

Respuesta paginada:
```json
{
  "content": [...],
  "pageable": {...},
  "totalPages": 5,
  "totalElements": 50,
  "size": 10,
  "number": 0
}
```

#### Obtener Productos Activos
```bash
GET /api/products/active?paginated=true&page=0&size=20
```

#### Buscar Productos
```bash
GET /api/products/search?keyword=laptop&paginated=true&page=0&size=10
```

#### Obtener Producto por ID
```bash
GET /api/products/1
```

#### Obtener Producto por Slug
```bash
GET /api/products/slug/laptop-dell-xps-13
```

#### Actualizar Producto Completo (PUT)
```bash
PUT /api/products/1
Authorization: Bearer {token}
Content-Type: application/json

{
  "subcategoryId": 1,
  "name": "Laptop Dell XPS 13 Updated",
  "description": "Descripción actualizada",
  "slug": "laptop-dell-xps-13",
  "sku": "DELL-XPS13-001",
  "price": 1399.99,
  "stockQuantity": 45
}
```

#### Actualizar Producto Parcial (PATCH)
```bash
PATCH /api/products/1
Authorization: Bearer {token}
Content-Type: application/json

{
  "price": 1099.99,
  "stockQuantity": 40
}
```

Campos que puedes actualizar con PATCH:
- `name`, `description`, `slug`, `sku`
- `price`, `discountPrice`
- `stockQuantity`, `weight`, `dimensions`
- `isActive`, `subcategoryId`

#### Eliminar Producto
```bash
DELETE /api/products/1
Authorization: Bearer {token}
```

### Carrito de Compras

#### Agregar al Carrito
```bash
POST /api/cart/add?productId=1&quantity=2
Authorization: Bearer {token}
```

#### Actualizar Cantidad
```bash
PUT /api/cart/update?productId=1&quantity=5
Authorization: Bearer {token}
```

#### Obtener Carrito
```bash
GET /api/cart
Authorization: Bearer {token}
```

#### Eliminar del Carrito
```bash
DELETE /api/cart/remove?productId=1
Authorization: Bearer {token}
```

#### Vaciar Carrito
```bash
DELETE /api/cart/clear
Authorization: Bearer {token}
```

### Órdenes

#### Crear Orden
```bash
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

#### Obtener Mis Órdenes
```bash
GET /api/orders/my-orders
Authorization: Bearer {token}
```

#### Obtener Orden por ID
```bash
GET /api/orders/1
Authorization: Bearer {token}
```

#### Actualizar Estado de Orden (PATCH)
```bash
PATCH /api/orders/1/status?status=PROCESSING
Authorization: Bearer {token}
```

Estados válidos:
- `PENDING`
- `PROCESSING`
- `SHIPPED`
- `DELIVERED`
- `CANCELLED`
- `REFUNDED`

## 🔄 Características Implementadas

### ✅ Redis Caching
- Los productos se cachean automáticamente
- Cache invalidado al crear, actualizar o eliminar productos
- TTL de cache: 10 minutos

### ✅ Paginación
- Disponible en endpoints de productos
- Parámetros configurables: page, size, sortBy, sortDir
- Compatible con búsqueda

### ✅ PATCH Endpoints
- Actualización parcial de productos
- Solo envía los campos que quieres actualizar
- Actualización de estado de órdenes

### ✅ Flyway Migrations
- Migraciones de base de datos versionadas
- Schema automáticamente creado al iniciar
- Located en `src/main/resources/db/migration`

### ✅ JWT Authentication
- Tokens con expiración de 24 horas
- Incluir en header: `Authorization: Bearer {token}`

### ✅ Singleton Pattern
- Implementado en servicios principales
- Thread-safe con double-checked locking

## 🗃️ Base de Datos

La base de datos se crea automáticamente con Flyway. Incluye:
- 13 tablas normalizadas (3NF)
- Índices optimizados
- Triggers para updated_at
- Constraints y foreign keys

## 🔧 Configuración

### application.properties
```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/ecommerce

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# JWT
jwt.secret=your-secret-key
jwt.expiration=86400000
```

## 📊 Monitoreo

### Ver logs de Docker Compose
```bash
docker-compose logs -f
```

### Conectar a PostgreSQL
```bash
docker exec -it <postgres-container> psql -U myuser -d ecommerce
```

### Conectar a Redis CLI
```bash
docker exec -it <redis-container> redis-cli
```

## 🛑 Detener Servicios

```bash
docker-compose down
```

Para eliminar también los volúmenes:
```bash
docker-compose down -v
```
