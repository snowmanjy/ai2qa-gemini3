# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## MANDATORY: RUN FULL TEST SUITE BEFORE COMPLETING TASKS

**Claude Code MUST run the full test suite before telling the user a task is complete.**

1. **Always run `mvn clean test`** (or equivalent) before saying any backend task is done
2. **Fix ALL test failures** - compilation errors and test failures must be resolved
3. **Update related tests** - when changing method signatures, update ALL test files that call those methods
4. **Never skip this step** - even for "simple" changes, tests must pass

**If Docker is not available** (Testcontainers failures), run unit tests only:
```bash
mvn clean test -Dtest='!*IntegrationTest'
```

---

## MANDATORY: FIX ALL SIMILAR ISSUES AT ONCE

**When fixing ANY bug or issue, Claude Code MUST:**

1. **Search the entire codebase** for similar patterns before making a fix
2. **Fix ALL occurrences** of the same issue in one pass, not one by one
3. **Check frontend AND backend** for type mismatches, enum inconsistencies, field name differences
4. **Verify API contracts** - ensure frontend types match backend DTOs exactly
5. **Never fix just one place** - always grep/search for the same pattern across all files

**Example:** If you find a field named `isOnline` in backend but frontend expects `online`:
- Search for ALL places using that field in both frontend and backend
- Check all related enums, types, DTOs, and queries
- Fix everything in one commit, not iteratively after user complaints

---

## MANDATORY: Adding New Personas Checklist

**When adding a new test persona (like PERFORMANCE_HAWK), Claude Code MUST update ALL of these locations:**

### Backend (Java)
- [ ] `ai2qa-domain-core/.../SystemConstants.java` - **UPDATE FIRST:** Increment `EXPECTED_PERSONA_COUNT`
- [ ] `ai2qa-domain-core/.../model/TestPersona.java` - Add enum value with temperature and system prompt
- [ ] Update `buildDisplayName()` switch statement in TestPersona.java
- [ ] Add tests for new persona in `TestPersonaTest.java`

### Database
- [ ] `ai2qa-infra-jpa/.../db/migration/V##__*.sql` - Add persona to `persona_definition` table (if using DB personas)
- [ ] Map any skills to the persona in the migration

### Frontend (TypeScript)
- [ ] `frontend/src/types/index.ts` - Add to `Persona` type union
- [ ] `frontend/src/components/dashboard/create-run/persona-selector.tsx` - Add persona card
- [ ] `frontend/src/components/dashboard/create-run-dialog.tsx` - Add to `PERSONA_TEMPLATES`
- [ ] `frontend/src/app/(public)/page.tsx` - Update persona showcase section
- [ ] `frontend/src/app/dashboard/page.tsx` - Update `getPersonaBadgeVariant()` and `getPersonaLabel()`
- [ ] `frontend/public/images/persona-*.png` - Add persona image

### Local Agent (if persona uses special tools)
- [ ] `ai2qa-local-agent/src/tools/` - Add any persona-specific tools
- [ ] `ai2qa-local-agent/src/server.ts` - Register new tools

**CRITICAL:** The `TestPersona` enum is the source of truth for persona resolution. If a persona is not in the enum, `resolvePersona()` in `TestRunService` will fall back to STANDARD silently!

---

## MANDATORY: Persona Metrics Data Flow

**When adding metrics collection for personas (Performance, A11y, I18n), you MUST update ALL layers of the data flow.**

### The Complete Data Flow

```
MCP Server (JS) → Domain Model (Java) → DTO/View (Java) → API Response → Frontend (TypeScript)
```

### Files at Each Layer

#### 1. MCP Server (Metrics Collection)
- **`ai2qa-mcp-bridge/src/main/resources/mcp-server/server.js`**
  - Collects raw metrics (e.g., `performance.getEntriesByType()`, `window.axe.run()`)
  - Returns metrics in the tool result JSON

#### 2. Domain Model (Pure Data)
- **`ai2qa-domain-core/.../model/PerformanceMetrics.java`** (or A11yMetrics, I18nMetrics)
  - Java Record with `Optional<T>` fields for absent data
  - NO validation logic in the record

#### 3. Application Service (Processing)
- **`ai2qa-application/.../TestRunExecutionService.java`**
  - Processes MCP tool results
  - Maps raw JSON to domain models
  - Stores metrics in `StepExecutionLog`

#### 4. DTO/View Layer (API Serialization)
- **`ai2qa-web-api/.../dto/StepExecutionLogDto.java`**
  - Exposes metrics to API consumers
  - Uses Jackson `@JsonInclude(NON_NULL)` for optional fields

#### 5. Frontend Types
- **`frontend/src/types/index.ts`**
  - TypeScript interface matching the DTO structure
  - All optional fields marked with `?`

#### 6. Frontend Components
- **`frontend/src/app/dashboard/runs/[id]/page.tsx`**
  - Maps API response to component props
  - **CRITICAL: Include ALL fields in mapping!**
- **`frontend/src/components/report/final-report.tsx`**
  - Renders metrics in the report UI

### Common Pitfall: Frontend Mapping

**This is where metrics often get "lost":**

```typescript
// WRONG - Metrics dropped during mapping
steps={logs.map(log => ({
    action: log.step?.action || 'unknown',
    target: log.step?.target || 'unknown',
    success: log.status === 'SUCCESS',
    durationMs: log.durationMs || 0
    // performanceMetrics is MISSING here!
}))}

// CORRECT - Include all metric fields
steps={logs.map(log => ({
    action: log.step?.action || 'unknown',
    target: log.step?.target || 'unknown',
    success: log.status === 'SUCCESS',
    durationMs: log.durationMs || 0,
    performanceMetrics: log.performanceMetrics,  // INCLUDE THIS
    a11yMetrics: log.a11yMetrics,                 // For A11y persona
    i18nMetrics: log.i18nMetrics                  // For I18n persona
}))}
```

### Checklist for Adding New Persona Metrics

- [ ] **MCP Server**: Add tool or modify existing tool to collect metrics
- [ ] **Domain Model**: Create `XxxMetrics.java` record in domain-core
- [ ] **StepExecutionLog**: Add `Optional<XxxMetrics>` field
- [ ] **DTO**: Add field to `StepExecutionLogDto`
- [ ] **Frontend Types**: Add TypeScript interface in `types/index.ts`
- [ ] **Page Component**: Include field in **ALL** mapping operations
- [ ] **Report Component**: Add UI section to display metrics
- [ ] **PDF Report**: Update `final-report.tsx` PDF rendering section
- [ ] **Verify in Browser**: Check Network tab that API returns the field
- [ ] **Verify in UI**: Check both summary section AND per-step display

### Debugging Tips

1. **Check API Response First**: Use browser DevTools → Network → find the API call → check response JSON
2. **If API has data but UI doesn't**: The bug is in frontend mapping
3. **If API doesn't have data**: Check backend DTO → Service → Domain flow
4. **If domain has data but DTO doesn't**: Check DTO field naming matches domain

---

## AI CODE QUALITY TOOLS

### Pre-Commit Hook (Installed)

A pre-commit hook runs unit tests automatically before each commit:
- Runs `mvn test` excluding integration tests (for speed)
- Checks for common AI-generated code mistakes (Lombok, null returns, Object types)
- Blocks commit if tests fail

**To bypass (emergency only):** `git commit --no-verify`

**To reinstall:** `cp scripts/hooks/pre-commit .git/hooks/pre-commit`

### AI Code Quality Checker

Run the quality checker manually to detect AI-generated code patterns:
```bash
./scripts/ai-code-quality-check.sh          # Normal mode (warnings only)
./scripts/ai-code-quality-check.sh --strict # Strict mode (exit 1 on errors)
```

**What it catches:**
- Lombok annotations (prohibited)
- `return null` (use Optional)
- `Object` as variable type
- Empty catch blocks
- System.out/printStackTrace
- Hardcoded secrets
- Raw generic types
- LocalDateTime instead of Instant

### Code Reviewer Agent

After completing significant code changes, Claude Code should proactively run the code-reviewer agent:
```
Use Task tool with subagent_type="pr-review-toolkit:code-reviewer"
```

This reviews code against CLAUDE.md standards before the user has to ask.

---

## MANDATORY: CLEAN ARCHITECTURE CHECKLIST

**Claude Code MUST review this checklist BEFORE writing ANY code in this repository.**

### Before Writing Domain Models (Records, Entities, Value Objects)

- [ ] **Pure data only** - No validation logic, no exceptions in constructors
- [ ] **Immutable** - All fields `final`, use Java Records where possible
- [ ] **No if/else** - No conditional logic in domain objects
- [ ] **Factory pattern** - Create separate Factory classes with `Optional<T>` return types
- [ ] **NO exceptions** - Never throw from domain model constructors or methods
- [ ] **NO nulls** - Use `Optional<T>` instead of null returns
- [ ] **NO validation in records** - Validation belongs in Factory classes
- [ ] **Never use "Object" as a variable type** - Type safety and correctness

### Before Writing Services & Business Logic

- [ ] **Functional style** - Use `map()`, `flatMap()`, `filter()`, streams
- [ ] **Strategy pattern** - Replace if/else chains with polymorphism or strategy
- [ ] **Optional/Mono** - Return `Optional<T>` or `Mono<T>` for expected failures
- [ ] **Reactive patterns** - Use Mono/Flux consistently for async operations
- [ ] **TDD: Write tests first** - Unit tests MUST exist before implementation
- [ ] **Calculation coverage** - ALL calculation logic MUST have unit tests
- [ ] **NO nested if/else** - Max 1 level of conditionals, prefer pattern matching
- [ ] **NO checked exceptions** - Use unchecked for truly exceptional cases only
- [ ] **NO untested calculation logic** - Every formula/algorithm needs test coverage
- [ ] **Never use "Object" as a variable type** - Type safety and correctness
- [ ] **Never disable existing tests** without discussing and permission
- [ ] **Fix all failing tests** - Never treat test failures as "pre-existing"; fix them immediately
- [ ] **Add tests for all changes** - Every code change requires corresponding unit/integration/E2E tests
- [ ] **Full build must pass** - Run `mvn clean test` and ensure ALL tests pass before completing work
- [ ] ** never disable tests** - either fix them, or remove them if useless anymore, but must confirm to get permission to delete

### Before Writing Controllers & API Boundaries

- [ ] **Validate at boundaries** - Controllers are the validation point, not domain
- [ ] **Thin controllers** - Delegate to services immediately
- [ ] **Fail fast** - Validate inputs at entry points
- [ ] **NO business logic in controllers** - Controllers handle HTTP only
- [ ] **Never use "Object" as a variable type** - Type safety and correctness

### Error Handling Rules

- [ ] **Optional for absence** - Use `Optional<T>` not null for missing values
- [ ] **Mono.error()** - Use reactive error handling in WebFlux
- [ ] **Custom unchecked exceptions** - For exceptional business cases
- [ ] **NO null returns** - Never return null from ANY method (including private helpers)
- [ ] **NO null in helper methods** - Private utility methods MUST also return `Optional<T>`

**If ANY rule is violated, STOP immediately and refactor to clean architecture principles.**

---

## Architecture Principles

### We Follow Clean Architecture

- Hexagonal Architecture / DDD
- Functional programming principles (map/flatMap, immutability, Optional, Either)
- Clean code (SRP, no long methods, no primitive obsession)
- TDD supportability (easy to mock, pure functions)
- No Spring annotations in domain; keep side effects in adapters
- Minimize if/else; use pattern matching, polymorphism, or strategy

```
Controllers → Use Cases (Services) → Domain Entities → Repositories
```

- Controllers should be thin - only handle HTTP concerns
- Services contain business logic
- Domain entities are rich, not anemic
- Repositories abstract data access

### Design Philosophy

- **Immutability First**: Use `final` fields, Java Records, and immutable collections
- **Fail Fast**: Validate at boundaries, use Optional to make null handling explicit
- **Functional Core, Imperative Shell**: Pure functions for business logic, side effects at edges
- **Tell, Don't Ask**: Objects should do things, not expose their state for others to manipulate
- **Keep it simple**: Write straightforward code, avoid over-engineering
- **YAGNI**: Don't implement features you don't need
- **DRY**: Don't repeat yourself

### SOLID Principles

- **Single Responsibility (SRP)**: Each class has one job
- **Open/Closed (OCP)**: Open for extension, closed for modification
- **Liskov Substitution (LSP)**: Subtypes must substitute for their base types
- **Interface Segregation (ISP)**: Clients should not depend on interfaces they do not use
- **Dependency Inversion (DIP)**: High-level modules depend on abstractions, not low-level modules

---

## Layered Architecture Rules

- **Rule 1:** Domain modules must have **zero dependencies** on framework libraries. Pure Java only.
- **Rule 2:** Dependencies flow **inwards**: `Infrastructure` → `Application` → `Domain`
- **Rule 3:** Controllers (Interface Layer) MUST NEVER depend on Repositories (Infrastructure Layer)
- **Rule 4:** All Controller logic must delegate to an Application Service

---

## MANDATORY: JPA/Hibernate Rules

**CRITICAL: Never create new entity objects when updating existing records.**

### The Problem: DDD Immutability vs JPA Session Management

This project uses **immutable domain objects** (DDD pattern) but JPA/Hibernate tracks entities by **Java object identity** within a session. These patterns clash:

```java
// What happens in DDD + JPA flow:
LocalAgent agent = repository.findById(id);  // Entity loaded into Hibernate session
LocalAgent updated = agent.revoke(now);      // DDD: creates NEW immutable object
repository.save(updated);                     // Converts to NEW entity → BOOM!

// Hibernate sees:
// - EntityA (id=123) already in session from findById()
// - EntityB (id=123) new object from toEntity()
// - Two different Java objects with same ID → NonUniqueObjectException
```

### The Rules

1. **NEVER use `toEntity()` for updates** - Only use it for INSERT operations
2. **Always check if entity exists FIRST** before saving
3. **Update the MANAGED entity in place** - Get it from the session, modify its fields
4. **Only create new entity objects for INSERT** - When the record doesn't exist yet

### WRONG Pattern (causes NonUniqueObjectException)

```java
@Override
public Domain save(Domain domain) {
    Entity entity = toEntity(domain);      // WRONG: Creates new detached entity
    Entity saved = jpaRepository.save(entity);  // Conflicts with session!
    return toDomain(saved);
}
```

### CORRECT Pattern (always use this)

```java
@Override
public Domain save(Domain domain) {
    // Check if entity already exists
    Optional<Entity> existing = jpaRepository.findById(domain.getId());

    Entity saved;
    if (existing.isPresent()) {
        // UPDATE: Modify the managed entity in place
        Entity entity = existing.get();
        entity.setField1(domain.getField1());
        entity.setField2(domain.getField2());
        saved = jpaRepository.save(entity);
    } else {
        // INSERT: Create new entity only for new records
        Entity entity = toEntity(domain);
        saved = jpaRepository.save(entity);
    }
    return toDomain(saved);
}
```

### Reference Implementation

See `TenantRepositoryImpl.save()` for the correct pattern. All repository `save()` methods MUST follow this pattern.

### Checklist Before Writing Repository save() Methods

- [ ] Does the method check if entity exists first?
- [ ] For updates: Am I modifying the MANAGED entity from `findById()`?
- [ ] For inserts: Am I only calling `toEntity()` when record doesn't exist?
- [ ] Entity class has setters for all updatable fields?

---

## Coding Style Constraints

### Functional Paradigm

```java
// BAD - Imperative loop
for (Event e : events) {
    save(e);
}

// GOOD - Functional stream
events.stream()
    .map(this::toEntity)
    .forEach(repo::save);
```

### Immutability

Use `final` keywords, Java Records, and `List.of()`/`Map.of()` factories.

### Time Handling

Use `java.time.Instant` for all timestamps. Avoid `LocalDateTime` to ensure UTC consistency.

### Layering

Controllers MUST call Services. Services MUST call Repositories. Never skip a layer.

---

## Factory & Creation Patterns

- **Complex Validation:** Use a dedicated Factory class when creation logic involves complex validation or invariants.
- **Hidden Constructors:** Hide public constructors of Aggregates (`private` or `protected`).
- **Factory classes** for creating new instances with invariant enforcement.
- **Static `reconstitute` method** for Infrastructure/Repositories to restore state from the database.
- **Builder pattern** instead of constructors with lots of parameters.

### Example: Pure Data + Factory Pattern

```java
// CORRECT - Pure data record, no validation
public record PriceData(LocalDate date, BigDecimal close) {}

// CORRECT - Validation in separate Factory
public class PriceDataFactory {
    public static Optional<PriceData> create(LocalDate date, BigDecimal close) {
        return Optional.ofNullable(date)
            .flatMap(d -> Optional.ofNullable(close))
            .filter(c -> c.compareTo(BigDecimal.ZERO) >= 0)
            .map(c -> new PriceData(date, close));
    }
}
```

### Functional Validation Chain

```java
// CORRECT - Chained validation returning Optional
public class BollingerBandsFactory {
    public static Optional<BollingerBands> create(
        BigDecimal upper,
        BigDecimal middle,
        BigDecimal lower
    ) {
        return Optional.ofNullable(upper)
            .flatMap(u -> Optional.ofNullable(middle))
            .flatMap(m -> Optional.ofNullable(lower))
            .filter(l -> upper.compareTo(middle) >= 0)
            .filter(l -> middle.compareTo(lower) >= 0)
            .map(l -> new BollingerBands(upper, middle, lower));
    }
}
```

### WRONG: Validation in Record Constructor

```java
// ABSOLUTELY WRONG - DO NOT DO THIS
public record PriceData(LocalDate date, BigDecimal close) {
    public PriceData {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (close.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
    }
}
```

---

## Domain Events Lifecycle

### Rules

**No Side Effects in Domain**: Domain Core knows nothing about the database.

---

## Lombok Usage Rules

**CRITICAL: DO NOT USE LOMBOK** in this project

Lombok has Java 21 compatibility issues:

- Annotation processing fails with `TypeTag :: UNKNOWN` errors
- Inline mocking (Mockito) incompatibilities

**What to do instead:**

```java
// WRONG - Do not use
@Slf4j
@Data
@AllArgsConstructor
public class MyClass {
    private final String name;
}

// CORRECT - Manual initialization
public class MyClass {
    private static final Logger log = LoggerFactory.getLogger(MyClass.class);
    private final String name;

    public MyClass(String name) {
        this.name = name;
    }
}
```

Use Java Records for immutable DTOs. Use constructor injection for dependencies.

---

## Coding Standards

### What Good Looks Like

#### Prefer Optional over null

```java
public Optional<Order> findOrder(OrderId id) {
    return orderRepository.findById(id);
}

// Usage
findOrder(orderId)
    .map(Order::execute)
    .orElseThrow(() -> new OrderNotFoundException(orderId));
```

#### Use Streams and functional style

```java
public BigDecimal calculateTotal(List<Item> items) {
    return items.stream()
        .map(this::calculateItemValue)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

#### Strategy Pattern over if/else

```java
public interface ExecutionStrategy {
    Result execute(Request request);
}

// ConcreteStrategyA, ConcreteStrategyB, etc.
```

#### Builder Pattern for complex objects

```java
Order order = Order.builder()
    .symbol(Symbol.of("AAPL"))
    .side(OrderSide.BUY)
    .quantity(Quantity.of(100))
    .orderType(OrderType.LIMIT)
    .limitPrice(Price.of(new BigDecimal("150.00")))
    .build();
```

### What Bad Looks Like

#### Returning null

```java
// BAD
public Order findOrder(OrderId id) {
    Order order = orderRepository.findById(id);
    if (order == null) {
        return null; // DON'T DO THIS
    }
    return order;
}
```

#### Nested if/else chains

```java
// BAD
public void processOrder(Order order) {
    if (order.getType() == OrderType.MARKET) {
        if (order.getSide() == OrderSide.BUY) {
            // ...
        } else {
            // ...
        }
    } else if (order.getType() == OrderType.LIMIT) {
        // ... more nesting
    }
}
```

#### Primitive obsession

```java
// BAD
public Trade executeTrade(String symbol, int quantity, double price) {}

// GOOD
public Trade executeTrade(Symbol symbol, Quantity quantity, Price price) {}
```

#### God classes / Anemic domain models

```java
// BAD - Service doing everything
public class OrderService {
    public void createOrder() {}
    public void validateOrder() {}
    public void executeOrder() {}
    public void cancelOrder() {}
    public void calculateFees() {}
    public void checkRiskLimits() {}
    // ... 50 more methods
}

// BAD - Anemic model
public class Order {
    private String symbol;
    private int quantity;
    // Only getters/setters, no behavior
}
```

#### Helper Methods Must Return Optional

```java
// WRONG - Private helper returning null
private BigDecimal extractBigDecimal(JsonNode node, String... fieldNames) {
    for (String fieldName : fieldNames) {
        JsonNode field = node.path(fieldName);
        if (!field.isMissingNode() && !field.isNull()) {
            return BigDecimal.valueOf(field.asDouble());
        }
    }
    return null;  // WRONG!
}

// CORRECT - Returns Optional
private Optional<BigDecimal> extractBigDecimal(JsonNode node, String... fieldNames) {
    for (String fieldName : fieldNames) {
        JsonNode field = node.path(fieldName);
        if (!field.isMissingNode() && !field.isNull()) {
            return Optional.of(BigDecimal.valueOf(field.asDouble()));
        }
    }
    return Optional.empty();
}
```

---

## Common Patterns We Use

### 1. Repository Pattern

All data access goes through repository interfaces. Never use JPA entities directly in business logic.

### 2. Command Pattern

For operations that modify state (CreateOrderCommand, CancelOrderCommand)

### 3. Factory Pattern

For creating complex objects with validation

### 4. Observer Pattern

For domain events and notifications

### 5. Result/Either Type

For error handling without exceptions in business logic

---

## Error Handling Strategy

- **Don't use checked exceptions** for business logic
- **Use custom unchecked exceptions** for exceptional cases
- **Use Result/Either types** for expected failures
- **Use Optional** for absence of values

```java
// Custom exceptions for exceptional cases
class NetworkException extends RuntimeException {
    NetworkException(String message) { super(message); }
}

// Either for expected failures
public Either<Failure, User> login(String email, String password) {
    if (email.isEmpty()) {
        return Either.left(new ValidationFailure("Email is required"));
    }
    try {
        User user = authService.login(email, password);
        return Either.right(user);
    } catch (NetworkException e) {
        return Either.left(new ServerFailure("Network error"));
    }
}
```

---

## Testing Approach

- **Unit tests**: Test business logic in isolation (domain layer)
- **Integration tests**: Test database interactions with Testcontainers
- **Component tests**: Test API endpoints
- Use **test builders** for creating test data
- Mock external dependencies (APIs, message brokers)

---

## Performance Considerations

- **Avoid N+1 queries**: Use JOIN FETCH in repositories
- **Cache frequently accessed data**: Use Redis or in-memory caching
- **Async processing**: Use @Async for non-blocking operations
- **Batch operations**: Process multiple items efficiently

---

## Security Requirements

- All API endpoints require authentication
- Validate user permissions before operations
- Audit trail for all changes
- Rate limiting on API endpoints

---

## When Refactoring

1. **Always write tests first** before refactoring
2. **Refactor in small increments** - one pattern/class at a time
3. **Run tests after each change** to ensure nothing breaks
4. **Update this documentation** if architecture changes
5. **Keep backward compatibility** for APIs unless explicitly breaking

---

## Questions to Ask Before Implementing

- Is this domain logic or infrastructure concern?
- Can this fail? If yes, how should we handle it? (Optional, Result, Exception?)
- Is this object immutable? Should it be?
- Are we following SOLID principles?
- Can we eliminate this if/else with polymorphism?
- Is this testable?

---

## Reactive Programming Patterns

### Mono/Flux Usage

- Return `Mono<T>` from service methods for async operations
- Use `.map()`, `.flatMap()`, `.filter()` for transformations
- Never block with `.block()` in non-test code
- Chain error handling with `.onErrorReturn()`, `.onErrorResume()`

### Example

```java
public Mono<TradingSignal> evaluate(Symbol symbol, MarketData data) {
    return historyClient.getIndicators(symbol, 252)
        .map(indicators -> createRequest(symbol, data, indicators))
        .flatMap(request -> evaluationService.evaluate(request))
        .onErrorReturn(TradingSignal.empty(symbol));
}
```

---

## Code Review Checklist

- [ ] No `null` returns anywhere?
- [ ] All domain records are immutable (final fields)?
- [ ] Optional used instead of null?
- [ ] No validation in domain constructors?
- [ ] No Lombok annotations (@Slf4j, @Data, etc.)?
- [ ] Monad/Optional chains used instead of if/else?
- [ ] Helper methods return Optional, not null?
- [ ] Tests exist for calculation/business logic?
- [ ] No `Object` as variable type?
- [ ] Using Instant for timestamps, not LocalDateTime?
- [ ] All tests pass? (`mvn clean test` runs without failures)
- [ ] New tests added for all code changes?
- [ ] No test failures left unfixed?
- [ ] **JPA save() methods check if entity exists before saving?**
- [ ] **JPA updates modify managed entity in place, not create new detached entity?**

When performing code review, remember to:
1. Traced execution paths across files
2. Verified contracts between components
3. Simulated runtime behavior mentally
4. Cross-referenced configuration with code