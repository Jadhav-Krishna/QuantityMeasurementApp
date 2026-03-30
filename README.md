# Quantity Measurement App

## Microservices Overview

This repository now builds the Quantity Measurement App as four Spring microservices:

| Service | Port | Why it exists |
| --- | --- | --- |
| `eureka-server` | `8761` | Service registry so the other services can find each other without hardcoding hostnames |
| `admin-server` | `9090` | Monitoring dashboard that shows health, metrics, loggers, and actuator details for registered services |
| `api-gateway` | `8080` | One public entry point that forwards `/api/measurements/**` and `/api/users/**` traffic |
| `measurement-service` | `8081` | Owns measurement conversion and computation logic plus its own H2 database |
| `user-service` | `8082` | Owns user profiles and user-specific conversion history in a separate H2 database |

### Why this split matters

- Single Responsibility: each service owns one concern, which keeps code easier to reason about and change.
- Loose Coupling: services talk over HTTP and service discovery instead of sharing classes or databases.
- Database per Service: `measurement-service` and `user-service` persist to their own H2 databases.
- Resilience: `measurement-service` still completes conversions even if `user-service` is unavailable; user-history sync is best effort.

### Startup order

1. Start `eureka-server`
2. Start `admin-server`
3. Start `measurement-service`
4. Start `user-service`
5. Start `api-gateway`

### Eureka registration flow

When a service boots, it registers itself with Eureka using its `spring.application.name`, host, and port. After that it sends periodic heartbeats so Eureka knows the instance is still alive, and other services query Eureka instead of hardcoding network addresses.

- `eureka-server` runs on `http://localhost:8761`
- `admin-server` runs on `http://localhost:9090`
- Open `http://localhost:8761` to verify registrations in the Eureka dashboard
- `measurement-service`, `user-service`, and `api-gateway` are configured to register themselves and fetch the live registry
- In this project, clients renew every `10` seconds and expire after `30` seconds of silence

### Monitoring with Admin Server

The admin server discovers applications through Eureka and reads their actuator endpoints. Open `http://localhost:9090` after the services are up to monitor health, metrics, logger configuration, and other runtime details in one dashboard.

### Gateway routing flow

Clients only need one public address: `http://localhost:8080`. The API gateway checks the incoming path, matches it against configured predicates, asks Eureka for the current target instance behind the `lb://` service name, and forwards the request internally.

- `/api/convert/**` is routed to `lb://measurement-service`
- `/api/measurements/**` is routed to `lb://measurement-service`
- `/api/users/**` is routed to `lb://user-service`
- A global logging filter records the incoming request and the completion time for every routed call

### Build and run

```bash
mvn test
mvn -pl eureka-server spring-boot:run
mvn -pl admin-server spring-boot:run
mvn -pl measurement-service spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

### Example API flow

Create a user:

```http
POST /api/users
Content-Type: application/json

{
  "name": "Asha",
  "email": "asha@example.com"
}
```

Convert kilometres to miles through the gateway:

```http
GET /api/convert/length?from=km&to=miles&value=10&userId=42
```

Compute values in the same category:

```http
POST /api/measurements/compute
Content-Type: application/json

{
  "userId": 1,
  "category": "WEIGHT",
  "operation": "ADD",
  "leftValue": 2,
  "leftUnit": "KG",
  "rightValue": 2.20462,
  "rightUnit": "LBS",
  "resultUnit": "KG"
}
```

Temperature currently supports conversion and comparison. Arithmetic is limited to length, weight, and volume to keep the service semantics clear.

### Complete end-to-end request flow

1. Client sends `GET http://localhost:8080/api/convert/length?from=km&to=miles&value=10&userId=42`
2. `api-gateway` matches `Path=/api/convert/**` and routes to `lb://measurement-service`
3. Eureka resolves `measurement-service` to the currently registered instance
4. `measurement-service` handles the call in `ConversionController -> ConversionService.convertLength()`
5. The conversion result is calculated as `10 km = 6.21 miles`
6. `measurement-service` calls `UserServiceClient.saveHistory(42, ...)` through Feign
7. `user-service` handles `POST /api/users/42/history` in `HistoryController` and stores the record in its own H2 database
8. The client receives a response shaped like `{"from":"km","to":"miles","input":10.0,"result":6.21}`

## **Building a Quantity Measurement System**

This document walks through the evolution of the Quantity Measurement codebase, where we progressively learned fundamental software design principles by solving increasingly complex problems. From basic equality comparisons to advanced arithmetic operations with selective support, this journey demonstrates real-world software evolution.

---

## **Final Architecture**

```
📂 N-Tier Architecture Overview
├── 📂 controller/
│   └── QuantityMeasurementController.java
├── 📂 service/
│   ├── IQuantityMeasurementService.java
│   └── QuantityMeasurementServiceImpl.java
├── 📂 repository/
│   ├── IQuantityMeasurementRepository.java
│   ├── QuantityMeasurementCacheRepository.java (Singleton)
│   └── QuantityMeasurementDatabaseRepository.java
├── 📂 model/
│   ├── QuantityModel.java
│   └── QuantityMeasurementEntity.java
├── 📂 dto/
│   └── QuantityDTO.java
├── 📂 exception/
│   ├── QuantityMeasurementException.java
│   └── DatabaseException.java
└── 📂 util/
    ├── ApplicationConfig.java
    └── ConnectionPool.java

📂 Core Domain
├── 📂 IMeasurable (interface)
│   ├── getConversionFactor()
│   ├── convertToBaseUnit()
│   ├── convertFromBaseUnit()
│   ├── getUnitName()
│   ├── supportsArithmetic() [default: true]
│   └── validateOperationSupport() [default: no-op]
│       ↑
│       ├──📂 LengthUnit (enum)
│       │   ├── FEET
│       │   ├── INCHES
│       │   ├── YARDS
│       │   └── CENTIMETERS
│       │
│       ├──📂 WeightUnit (enum)
│       │   ├── KILOGRAM
│       │   ├── GRAM
│       │   └── POUND
│       │
│       ├──📂 VolumeUnit (enum)
│       │   ├── LITRE
│       │   ├── MILLILITRE
│       │   └── GALLON
│       │
│       └──📂 TemperatureUnit (enum) [arithmetic disabled]
│           ├── CELSIUS
│           ├── FAHRENHEIT
│           └── KELVIN
│
├── 📂 SupportsArithmetic (functional interface)
│   └── boolean isSupported()
│
└── 📂 Quantity<U extends IMeasurable> (generic class)
    ├── value: double
    ├── unit: U
    ├── equals()
    ├── convertTo()
    ├── add() / add(other, targetUnit)
    ├── subtract() / subtract(other, targetUnit)
    ├── divide()
    └── ArithmeticOperation (private enum)
        ├── ADD
        ├── SUBTRACT
        └── DIVIDE

📂 QuantityMeasurementApp (Singleton + Factory)
    ├── getInstance()
    ├── getController()
    ├── createRepository()
    ├── createController()
    ├── createService()
    └── createQuantityDTO()

📂 Resources
├── application.properties
├── logback.xml
└── db/
    ├── schema.sql
    └── schema-postgresql.sql

📂 Test Resources
└── db/schema-h2.sql
```

---

## **UC1: Basic Feet Equality - The Foundation**

### **What we did:**
- Created a simple `Feet` class to represent measurements
- Implemented basic equality comparison: "Is 1 foot equal to 1 foot?"

### **What we learned:**
- **Value objects**: Objects that represent a concept by their value, not identity
- **Overriding equals()**: How to customize equality comparison in Java
- **Test-Driven Development (TDD)**: Writing tests first, then implementation

### **Key concept:**
```java
Feet f1 = new Feet(1.0);
Feet f2 = new Feet(1.0);
f1.equals(f2); // true - same value
```

---

## **UC2: Cross-Unit Comparison (Feet + Inches)**

### **What we did:**
- Extended equality to compare different units: "Is 1 foot equal to 12 inches?"
- Introduced conversion logic to compare apples to apples

### **What we learned:**
- **Normalization**: Converting different representations to a common base
- **Conversion factors**: Mathematical relationships between units (1 foot = 12 inches)
- **Base unit concept**: Choosing one unit as the reference (base)

### **Problem solved:**
```java
Length feet = new Length(1.0, FEET);
Length inches = new Length(12.0, INCHES);
feet.equals(inches); // true - equivalent values
```

---

## **UC3: Generic Length Class with DRY Principle**

### **What we did:**
- Replaced separate `Feet` and `Inches` classes with generic `Length` class
- Added `LengthUnit` enum to represent different units

### **What we learned:**
- **DRY (Don't Repeat Yourself)**: Eliminate code duplication
- **Enums**: Type-safe way to represent fixed sets of constants
- **Composition**: Combining value + unit instead of separate classes
- **Single class, multiple units**: More scalable than one class per unit

### **Design evolution:**
```
Before: Feet class, Inches class, Yards class...
After:  Length class + LengthUnit enum
```

---

## **UC4: Adding More Units (Yards + Centimeters)**

### **What we did:**
- Added YARDS and CENTIMETERS to the `LengthUnit` enum
- Made sure all units work seamlessly with existing code

### **What we learned:**
- **Open-Closed Principle (OCP)**: Open for extension, closed for modification
- **Scalability**: Adding units is now easy - just add enum constants
- **Consistency**: All units follow the same pattern

### **Scalability demonstration:**
```java
// Adding a new unit is just one line!
enum LengthUnit {
    FEET(12.0),
    INCHES(1.0),
    YARDS(36.0),
    CENTIMETERS(0.393701) // New unit added easily
}
```

---

## **UC5: Unit Conversion Operations**

### **What we did:**
- Added `convert()` and `convertTo()` methods
- Implemented actual unit conversion, not just comparison

### **What we learned:**
- **Static utility methods**: `Length.convert(value, from, to)` for conversions
- **Immutability**: Operations return new objects instead of modifying existing ones
- **Precision handling**: Rounding to 2 decimal places to manage floating-point errors

### **Usage:**
```java
Length feet = new Length(1.0, FEET);
Length inches = feet.convertTo(INCHES); // Returns new Length(12.0, INCHES)
```

---

## **UC6: Addition - Same and Different Units**

### **What we did:**
- Implemented addition of quantities in same or different units
- Result inherits the unit of the first operand

### **What we learned:**
- **Method overloading**: Multiple versions of `add()` method
- **Unit normalization**: Convert to base unit, add, convert back
- **Operator design**: Choosing sensible defaults (result in first operand's unit)

### **Example:**
```java
Length l1 = new Length(1.0, FEET);
Length l2 = new Length(12.0, INCHES);
Length result = l1.add(l2); // Returns 2.0 FEET
```

---

## **UC7: Addition with Explicit Target Unit**

### **What we did:**
- Added `add(other, targetUnit)` method
- User specifies desired result unit

### **What we learned:**
- **API flexibility**: Giving users control over output format
- **Method overloading patterns**: Convenience method + explicit method
- **Default parameters**: Using overloading to simulate default arguments

### **User control:**
```java
l1.add(l2, YARDS);  // Result in yards
l1.add(l2, INCHES); // Result in inches
```

---

## **UC8: Standalone Enum with Conversion Responsibility**

### **What we did:**
- Extracted `LengthUnit` from nested enum to standalone enum
- Moved conversion logic INTO the enum itself
- Changed base unit from feet to inches

### **What we learned:**
- **Separation of Concerns**: Enum handles conversions, class handles operations
- **Delegation**: `Length` delegates to `LengthUnit` for conversions
- **Enum as behavior carrier**: Enums can have methods, not just constants
- **Refactoring without breaking**: All tests still pass after refactoring

### **Architecture improvement:**
```java
// Enum now has intelligence
public enum LengthUnit {
    FEET(12.0);
    
    private final double conversionFactor;
    
    LengthUnit(double conversionFactor) {
        this.conversionFactor = conversionFactor;
    }
    
    public double convertToBaseUnit(double value) {
        return value * conversionFactor;
    }
    
    public double convertFromBaseUnit(double baseValue) {
        return baseValue / conversionFactor;
    }
}
```

---

## **UC9: Multi-Category Support (Weight Measurements)**

### **What we did:**
- Created `WeightUnit` enum (KILOGRAM, GRAM, POUND)
- Created `Weight` class mirroring `Length` pattern
- Added demonstration methods for weight operations

### **What we learned:**
- **Pattern replication**: Following established patterns for new features
- **Category separation**: Weight and Length are incompatible (can't compare)
- **Type safety**: instanceof checks prevent cross-category comparisons

### **Problem introduced:**
```
Code duplication: Length and Weight classes are nearly identical
WeightUnit and LengthUnit have duplicate structure
QuantityMeasurementApp has duplicate methods
Not scalable: Adding Volume or Temperature means more duplication
```

---

## **UC10: Generic Architecture - The Breakthrough**

### **What we did:**
- Created `IMeasurable` interface - contract for all unit types
- Created generic `Quantity<U extends IMeasurable>` class
- Made both `LengthUnit` and `WeightUnit` implement `IMeasurable`
- Simplified `QuantityMeasurementApp` to use generic methods

### **What we learned:**

#### 1. Generic Programming
```java
Quantity<LengthUnit> length = new Quantity<>(1.0, FEET);
Quantity<WeightUnit> weight = new Quantity<>(1.0, KILOGRAM);
```
- One class works with ANY unit type
- Compile-time type safety
- No code duplication

#### 2. Interface-Based Design
```java
public interface IMeasurable {
    double getConversionFactor();
    double convertToBaseUnit(double value);
    double convertFromBaseUnit(double baseValue);
    String getUnitName();
}
```
- Defines a contract all units must follow
- Enables polymorphism
- Allows treating different unit types uniformly

#### 3. Bounded Type Parameters
```java
public class Quantity<U extends IMeasurable>
```
- `U` can be ANY type that implements `IMeasurable`
- Compiler enforces this constraint
- Type safety without sacrificing flexibility

#### 4. DRY Principle Mastered
- **Before UC10**: Duplicate Length and Weight classes
- **After UC10**: Single Quantity class for ALL categories
- **Impact**: ~200 lines of code eliminated

#### 5. Single Responsibility Principle
```java
// Before: 10 methods (5 for length, 5 for weight)
demonstrateLengthEquality()
demonstrateWeightEquality()
demonstrateLengthConversion()
demonstrateWeightConversion()
...

// After: 5 generic methods
demonstrateEquality<U>()     // Works for ALL types
demonstrateConversion<U>()   // Works for ALL types
...
```

#### 6. Open-Closed Principle
- System is **OPEN** for adding new categories
- System is **CLOSED** for modification

**Adding a new category (e.g., Volume):**
```java
// 1. Create enum implementing IMeasurable
public enum VolumeUnit implements IMeasurable {
    LITER(1.0),
    MILLILITER(0.001),
    GALLON(3.78541);
    // ... implement interface methods
}

// 2. Use it immediately - NO OTHER CHANGES NEEDED!
Quantity<VolumeUnit> vol = new Quantity<>(1.0, LITER);
vol.equals(new Quantity<>(1000.0, MILLILITER)); // Works!
```

#### 7. Liskov Substitution Principle
- Any `IMeasurable` implementation can be used with `Quantity<U>`
- No special cases needed
- Substitutable without breaking functionality

#### 8. Type Erasure Handling
```java
// Generic type info erased at runtime, so we check manually
if (this.unit.getClass() != that.unit.getClass())
    return false; // Prevents comparing length to weight
```

#### 9. Polymorphism
```java
IMeasurable unit = LengthUnit.FEET;  // Polymorphic reference
unit.convertToBaseUnit(1.0);          // Works!

unit = WeightUnit.KILOGRAM;           // Different type
unit.convertToBaseUnit(1.0);          // Still works!
```

---

## **UC11: Volume Measurements - Testing Generic Architecture**

### **What we did:**
- Created `VolumeUnit` enum (LITRE, MILLILITRE, GALLON)
- Implemented volume-to-volume conversions
- Added volume addition operations
- Applied precision rounding (2 decimal places)

### **What we learned:**
- **Architecture validation**: UC10's generic design works perfectly for new categories
- **Precision management**: Floating-point arithmetic requires rounding strategies
- **Zero-modification extension**: Added entire category without changing existing code

### **Key implementation:**
```java
public enum VolumeUnit implements IMeasurable {
    LITRE(1.0),
    MILLILITRE(0.001),
    GALLON(3.78541);
    
    // Rounding prevents floating-point errors
    @Override
    public double convertToBaseUnit(double value) {
        double result = value * conversionFactor;
        return Math.round(result * 100.0) / 100.0;
    }
}
```

### **Problem solved:**
```java
Quantity<VolumeUnit> gallon = new Quantity<>(1.0, GALLON);
Quantity<VolumeUnit> litre = new Quantity<>(3.78, LITRE);
gallon.equals(litre); // true - equivalent after rounding

Quantity<VolumeUnit> sum = gallon.add(litre); // 2.0 GALLON
```

---

## **UC12: Subtraction and Division - Expanding Arithmetic Operations**

### **What we did:**
- Implemented `subtract()` method with same/explicit target unit
- Implemented `divide()` method returning ratio
- Added comprehensive validation for all arithmetic operations
- Centralized validation logic to avoid duplication

### **What we learned:**
- **Consistent API design**: Subtraction mirrors addition's dual-method pattern
- **Division semantics**: Returns scalar (double), not Quantity
- **Validation patterns**: Consistent error handling across operations
- **Edge case handling**: Division by zero protection with epsilon comparison

### **Key implementations:**

#### Subtraction:
```java
Quantity<LengthUnit> l1 = new Quantity<>(5.0, FEET);
Quantity<LengthUnit> l2 = new Quantity<>(3.0, FEET);
Quantity<LengthUnit> diff = l1.subtract(l2); // 2.0 FEET

// With explicit target unit
Quantity<LengthUnit> diffInches = l1.subtract(l2, INCHES); // 24.0 INCHES
```

#### Division:
```java
Quantity<LengthUnit> l1 = new Quantity<>(6.0, FEET);
Quantity<LengthUnit> l2 = new Quantity<>(3.0, FEET);
double ratio = l1.divide(l2); // 2.0 (dimensionless)
```

### **Validation strategy:**
```java
private void validateArithmeticOperands(Quantity<U> other, U targetUnit, boolean targetUnitRequired) {
    // Null check
    // Category compatibility check
    // Finite value check
    // Target unit check (conditional)
}
```

---

## **UC13: Centralized Arithmetic Logic - DRY at Operation Level**

### **What we did:**
- Created `ArithmeticOperation` enum encapsulating operation logic
- Refactored `add()`, `subtract()`, `divide()` to use centralized helpers
- Eliminated code duplication across arithmetic methods
- Introduced functional programming with `DoubleBinaryOperator`

### **What we learned:**
- **Strategy Pattern**: Enum-based strategy for different operations
- **Lambda expressions**: Using lambdas for operation logic
- **Template Method Pattern**: Shared validation + operation + conversion flow
- **DRY at algorithm level**: Don't repeat the "convert → operate → convert back" pattern
- **Code reduction**: 50% fewer lines of code with improved maintainability

### **Architecture breakthrough:**

#### ArithmeticOperation Enum:
```java
private enum ArithmeticOperation {
    ADD((a, b) -> a + b),
    SUBTRACT((a, b) -> a - b),
    DIVIDE((a, b) -> {
        if (Math.abs(b) < EPSILON) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        return a / b;
    });
    
    private final DoubleBinaryOperator operation;
    
    ArithmeticOperation(DoubleBinaryOperator operation) {
        this.operation = operation;
    }
    
    public double compute(double a, double b) {
        return operation.applyAsDouble(a, b);
    }
}
```

#### Centralized Helper:
```java
private double performBaseArithmetic(Quantity<U> other, ArithmeticOperation operation) {
    double thisBase = unit.convertToBaseUnit(this.value);
    double otherBase = other.unit.convertToBaseUnit(other.value);
    return operation.compute(thisBase, otherBase);
}

// Usage:
double result = performBaseArithmetic(other, ArithmeticOperation.SUBTRACT);
```

### **Benefits achieved:**
1. **Single source of truth**: Operation logic defined once
2. **Easy extension**: Adding multiply/modulo means adding one enum constant
3. **Consistent behavior**: All operations use same validation flow
4. **Testability**: Can test operations independently
5. **Functional programming**: Leveraging Java's functional capabilities

---

## **UC14: Temperature Measurements - Selective Arithmetic Support**

### **What we did:**
- Created `TemperatureUnit` enum (CELSIUS, FAHRENHEIT, KELVIN)
- Implemented **non-linear** conversions (temperature has offset, not just scaling)
- Created `SupportsArithmetic` functional interface
- Extended `IMeasurable` with arithmetic support checking
- Made arithmetic operations validate unit support
- **Key insight**: Temperature doesn't support addition/subtraction of absolute values

### **What we learned:**

#### 1. Non-Linear Conversions
```java
public enum TemperatureUnit implements IMeasurable {
    CELSIUS(c -> c),
    FAHRENHEIT(f -> (f - 32) * 5 / 9),  // Offset + scaling
    KELVIN(k -> k - 273.15);
    
    private final Function<Double, Double> conversionToBase;
    
    @Override
    public double convertToBaseUnit(double value) {
        return conversionToBase.apply(value);
    }
    
    @Override
    public double convertFromBaseUnit(double baseValue) {
        switch (this) {
            case FAHRENHEIT: return (baseValue * 9 / 5) + 32;
            case KELVIN: return baseValue + 273.15;
            default: return baseValue;
        }
    }
}
```

**Why non-linear matters:**
- Linear: 1 foot + 1 foot = 2 feet 
- Non-linear: 10°C + 10°C ≠ 20°C  (meaningless operation)

#### 2. Functional Interface for Arithmetic Support
```java
@FunctionalInterface
public interface SupportsArithmetic {
    boolean isSupported();
}
```

#### 3. Enhanced IMeasurable Interface
```java
public interface IMeasurable {
    // Existing methods...
    
    // New methods:
    default SupportsArithmetic supportsArithmetic() {
        return () -> true;  // Default: supports arithmetic
    }
    
    default boolean supportsArithmetic() {
        return supportsArithmetic.isSupported();
    }
    
    default void validateOperationSupport(String operation) {
        // Default: no-op (operations allowed)
    }
}
```

#### 4. Temperature Overrides
```java
public enum TemperatureUnit implements IMeasurable {
    // ...
    
    private final SupportsArithmetic supportsArithmetic = () -> false;
    
    @Override
    public boolean supportsArithmetic() {
        return supportsArithmetic.isSupported();
    }
    
    @Override
    public void validateOperationSupport(String operation) {
        throw new UnsupportedOperationException(
            "Temperature does not support operation: " + operation + 
            " on absolute values.");
    }
}
```

#### 5. Updated Quantity Methods
```java
public Quantity<U> add(Quantity<U> other) {
    if (other == null) {
        throw new IllegalArgumentException("Quantity cannot be null");
    }
    
    // NEW: Check if this unit type supports arithmetic
    this.unit.validateOperationSupport("ADD");
    other.unit.validateOperationSupport("ADD");
    
    return add(other, this.unit);
}
```

### **Why this matters:**

#### The Temperature Problem:
```java
// These make sense:
Quantity<TemperatureUnit> temp1 = new Quantity<>(0.0, CELSIUS);
Quantity<TemperatureUnit> temp2 = new Quantity<>(32.0, FAHRENHEIT);
temp1.equals(temp2); // true - comparison works!
temp1.convertTo(KELVIN); // 273.15 KELVIN - conversion works!

// These DON'T make sense:
temp1.add(temp2); // Throws UnsupportedOperationException
temp1.subtract(temp2); // Throws UnsupportedOperationException
temp1.divide(temp2); // Throws UnsupportedOperationException
```

**Physical reasoning:**
- **Temperature** represents a point on a scale (absolute value)
- **Temperature difference** would be a different concept (interval/delta)
- Adding 20°C + 30°C = 50°C is physically meaningless
- But: 30°C - 20°C = 10°C **difference** makes sense (but returns different type)

### **Design patterns applied:**

1. **Template Method**: Default implementations with override points
2. **Strategy Pattern**: SupportsArithmetic strategy for each unit type
3. **Fail-Fast Principle**: Validate before executing operation
4. **Liskov Substitution**: TemperatureUnit still substitutable, just throws on invalid ops
5. **Functional Programming**: Lambda-based conversion functions

### **Architectural flexibility:**
```java
// Easy to add selective support for future units
public enum PressureUnit implements IMeasurable {
    // Pressure supports all arithmetic operations
    // No overrides needed - uses defaults
}

public enum DateUnit implements IMeasurable {
    // Dates support subtraction (difference) but not addition
    private final SupportsArithmetic supportsArithmetic = () -> false;
    
    @Override
    public void validateOperationSupport(String operation) {
        if (operation.equals("SUBTRACT")) {
            return; // Allow subtraction
        }
        throw new UnsupportedOperationException(
            operation + " not supported for dates");
    }
}
```

---

## **UC15: N-Tier Architecture Refactoring**

### **What we did:**
- Refactored into N-tier layers: `controller`, `service`, `repository`, `model`, `dto`, `exception`
- Added clean interfaces (`IQuantityMeasurementService`, `IQuantityMeasurementRepository`)
- Kept business logic in service, orchestration in controller, persistence in repository
- Used DI + factory methods so repository implementation can be swapped

### **Outcome:**
- Better maintainability and testability
- Backward compatibility with UC1–UC14 preserved
- Base foundation for JDBC persistence (UC16)

---

## **UC16: Database Integration with JDBC**

### **What we did:**
- Implemented `QuantityMeasurementDatabaseRepository` with JDBC
- Added HikariCP connection pooling
- Created SQL schemas for H2 and PostgreSQL

### **Key concepts:**
- **Connection pooling** for efficient database access
- **Prepared statements** for SQL injection prevention
- **Repository pattern** for data access abstraction

---

## **UC17: Spring Boot REST Services and JPA**

### **What we did:**
- Migrated to Spring Boot 4.0.3 with Spring Data JPA
- Created REST API endpoints for all quantity operations
- Replaced JDBC with JPA repositories
- Added Spring Security foundation

### **Key concepts:**
- **Spring Boot auto-configuration** eliminates boilerplate
- **Spring Data JPA** provides repository implementations
- **REST controllers** with `@RestController`, `@RequestMapping`
- **DTO pattern** separates API contracts from entities

### **API Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/quantities/compare` | Compare two quantities |
| POST | `/api/v1/quantities/add` | Add quantities |
| POST | `/api/v1/quantities/subtract` | Subtract quantities |
| POST | `/api/v1/quantities/divide` | Divide quantities |
| GET | `/api/v1/quantities/history` | Get operation history |

---

## **UC18: Google OAuth2 Authentication and User Management**

### **What we did:**
- Integrated Google OAuth2 for secure authentication
- Created `User` entity with Google profile data
- Added user-measurement ownership (ManyToOne relationship)
- Configured multi-database support (PostgreSQL, MySQL, H2)

### **Key concepts:**
- **OAuth2 flow**: User → Google → Callback → JWT/Session
- **CustomOAuth2UserService** persists Google user info
- **CustomOAuth2User** wraps OAuth2User with our User entity
- **@AuthenticationPrincipal** injects authenticated user into controllers

### **New Components:**
```
├── model/User.java                    # User entity with OAuth2 fields
├── repository/UserRepository.java     # JPA repository for users
├── security/
│   ├── CustomOAuth2UserService.java   # Persists Google users
│   ├── CustomOAuth2User.java          # Principal wrapper
│   └── OAuth2AuthenticationSuccessHandler.java
├── controller/AuthController.java     # Auth endpoints
└── dto/UserDTO.java                   # User data transfer object
```

### **User-Specific Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/auth/user` | Get current user info |
| GET | `/auth/status` | Check authentication status |
| GET | `/api/v1/quantities/my/history` | User's operation history |

### **Configuration Profiles:**
- `dev` - H2 in-memory, debug logging
- `test` - H2 for automated tests
- `mysql` - MySQL database
- (default) - PostgreSQL production

### **Environment Variables:**
```bash
GOOGLE_CLIENT_ID=your-client-id
GOOGLE_CLIENT_SECRET=your-client-secret
DATABASE_URL=jdbc:postgresql://localhost:5432/quantity_measurement_db
```

---

## **Quick Start**

```bash
# Development (H2)
mvn spring-boot:run -Dspring.profiles.active=dev

# Production (PostgreSQL + OAuth2)
export GOOGLE_CLIENT_ID=xxx GOOGLE_CLIENT_SECRET=xxx
mvn spring-boot:run
```

## **Tests**
```bash
mvn test  # 32 tests passing
```
