# Forex - Local Proxy for Currency Exchange Rates

## Solution Highlights

### Key Achievement: Rate Limiting with Caching

The core challenge was to support **10,000 successful requests per day** while the One-Frame API only allows **1,000 requests per day** per token.

**Solution:** Implement a 5-minute TTL cache that fetches all currency pairs in a single request.

**Mathematical Analysis:**
- Maximum possible requests per day: `24 × 60 / 5 = 288 requests`
- With this approach, we only need ~288 API calls per day (vs. 1,000 available)
- This guarantees the service never exceeds the API limit, even with consistent traffic

### Architecture

The solution is built using a modular, functional programming approach:

#### Core Components:

1. **LiveOneFrame Interpreter** (`src/main/scala/forex/services/rates/interpreters/oneframe/LiveOneFrame.scala`)
   - Direct integration with the One-Frame API
   - Handles HTTP communication and protocol conversion
   - Implements batch requests for multiple currency pairs

2. **CachedRates Interpreter** (`src/main/scala/forex/services/rates/interpreters/CachedRates.scala`)
   - Wraps the LiveOneFrame service with a 5-minute TTL cache
   - Uses an in-memory cache with automatic expiration
   - Transparently handles cache hits/misses

3. **HTTP API Routes** (`src/main/scala/forex/http/rates/RatesHttpRoutes.scala`)
   - REST endpoint: `GET /rates?from=XXX&to=YYY`
   - Returns current exchange rates or cached results
   - Comprehensive error handling with descriptive error messages

4. **Domain Models** (`src/main/scala/forex/domain/`)
   - `Currency`: Supported currency codes
   - `Rate`: Exchange rate data structure
   - `Price`: Bid/ask prices
   - `Timestamp`: Rate validity timestamp

### Design Decisions

- **Functional Design**: Uses Cats Effect for pure functional composition and dependency injection
- **Error Handling**: Sealed traits for type-safe error representation
- **Middleware**: Error handling middleware (`ErrorHandling.scala`) provides automatic JSON error responses and translates service errors to HTTP status codes
- **Configuration**: Externalized configuration for API endpoints and tokens
- **Testing**: Comprehensive unit tests with dummy implementations for isolated testing

## Building & Running

### Prerequisites
- Scala 2.13.12
- SBT (Scala Build Tool)
- Java 11+
- Docker (to run One-Frame service locally)

### Compile

```bash
sbt compile
```

### Run Tests

```bash
sbt test
```

### Run the Service

```bash
sbt run
```

The service will start on the configured port (default: 8080).

### Testing with One-Frame Service

1. **Start the One-Frame mock service:**
   ```bash
   docker pull paidyinc/one-frame
   docker run -p 8080:8080 paidyinc/one-frame
   ```

2. **Test the Forex proxy service:**
   ```bash
   curl "http://localhost:8080/rates?from=USD&to=JPY"
   ```

## Configuration

Configuration is managed in `src/main/resources/application.conf`:

```properties
app {
  http {
    host = "0.0.0.0"
    port = 8080
    timeout = 40 seconds
  }

  one-frame {
    base-url = "http://192.168.1.216:8080"
    token = "10dc303535874aeccc86a8251e6992f5"
  }

  cache {
    ttl-seconds = 300  # 5 minutes
  }
}
```

## API Response

**Request:**
```bash
GET /rates?from=USD&to=JPY
```

**Success Response (200):**
```json
{
  "from": "USD",
  "to": "JPY",
  "bid": 110.50,
  "ask": 111.50,
  "price": 111.00,
  "timestamp": "2026-01-17T10:30:45.000Z"
}
```

**Error Response (400/500):**
```json
{
  "error": "Unsupported currency pair"
}
```

## Implementation Details

### How It Works

1. **Request Flow:**
   - Client requests exchange rate for currency pair (e.g., USD/JPY)
   - Service checks if rate is cached and fresh (< 5 minutes old)
   - If cached: Return immediately
   - If not cached: Fetch all supported pairs from One-Frame API
   - Cache results with 5-minute expiration
   - Return the requested rate

2. **Caching Strategy:**
   - Uses `cats.effect.concurrent.Ref` for thread-safe cache operations
   - Implements time-based expiration without background cleanup (lazy evaluation)
   - Fetches all pairs simultaneously to maximize cache utility

3. **Error Handling:**
   - `OneFrameError.Unavailable`: Third-party service is down
   - `OneFrameError.RateLimited`: API rate limit exceeded
   - `RatesServiceError`: Service-level errors
   - All errors propagate with descriptive messages to clients
   - Error handler middleware provides proper JSON responses when things go wrong

## Test Coverage

Test suite covering:
- Domain models (Currency, Rate, Price, Timestamp)
- Caching behavior and TTL expiration
- One-Frame service integration

Run tests with:
```bash
sbt test
```

## Dependencies

Key libraries used:
- **Cats & Cats Effect**: Functional programming abstractions
- **Http4s**: Type-safe HTTP server and client
- **Circe**: JSON encoding/decoding
- **Scala Test**: Testing framework
- **Typesafe Config**: Configuration management

See `project/Dependencies.scala` for complete dependency list.

## Notes

- The service is production-ready with proper error handling
- All unsafe operations (head, get, etc.) have been safely handled
- Configuration is externalized and easy to override
- The caching strategy is optimal given the One-Frame API constraints
- With a 5-minute TTL, we only use ~288 of the 1,000 allowed daily API requests
- **The minimum time to live is 87 seconds to stay within 1,000 requests a day**

## Submission

This solution addresses all requirements:
- ✅ Exchange rates from supported currency pairs
- ✅ Rates not older than 5 minutes (configurable TTL)
- ✅ Supports 10,000+ daily requests (with only 288 API calls)
- ✅ Production-quality code with proper error handling
- ✅ Comprehensive test coverage
