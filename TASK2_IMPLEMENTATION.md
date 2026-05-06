# Task 2: Payment Service Refinement - Implementation Summary

## Overview
Implemented a robust Payment Service with idempotency checks, external payment gateway integration (VNPay/Momo mock), 2-second delay simulation, and proper RabbitMQ messaging using the centralized exchange and routing keys from common-library.

## Key Components Implemented

### 1. **Payment Entity Enhancement** (`Payment.java`)
Added fields for tracking payment attempts and gateway information:
- `paymentGateway` - Tracks which gateway processed the payment (VNPay/Momo)
- `retryCount` - Counts retry attempts for idempotency
- `lastRetryAt` - Tracks the last retry timestamp
- Status field now defaults to `PENDING` on creation

### 2. **PaymentStatus Enum Enhancement**
Updated to include three states:
- `PENDING` - Initial state when payment is received
- `COMPLETED` - Payment successful
- `FAILED` - Payment failed

### 3. **External Payment Gateway Integration**

#### PaymentGateway Interface (`gateway/PaymentGateway.java`)
Defines the contract for payment gateway implementations:
```java
PaymentGatewayResponse processPayment(String orderId, String transactionId, BigDecimal amount)
```

#### VNPay Mock Implementation (`gateway/impl/VNPayGateway.java`)
- Simulates 2-second network delay
- Mock success rate: 80%
- Returns reference code with `VNP_` prefix
- Logs all transaction details

#### Momo Mock Implementation (`gateway/impl/MomoGateway.java`)
- Simulates 2-second network delay  
- Mock success rate: 85%
- Returns reference code with `MOMO_` prefix
- Logs all transaction details

#### PaymentGatewayResponse Record
Simple DTO for gateway responses:
```java
record PaymentGatewayResponse(
    boolean success,
    String message,
    String referenceCode
)
```

### 4. **PaymentService - Core Logic** (`PaymentService.java`)

#### Idempotency Implementation
```java
// Check if order already has completed payment
var existingPayment = paymentRepository.findByOrderId(orderId);
if (existingPayment.isPresent() && 
    existingPayment.get().getStatus() == PaymentStatus.COMPLETED) {
    // Skip duplicate payment processing
    // Send existing result back
    return;
}
```

#### Payment Processing Flow
1. Receive payment command from Order Service
2. Check for existing completed payment (idempotency)
3. Generate unique transaction ID
4. Choose payment gateway (alternating VNPay/Momo)
5. Call external gateway (simulates 2s delay)
6. Update payment status based on gateway response
7. Send result back to Order Service via `payment.reply.result` routing key

#### Key Methods
- `pay(PaymentRequest)` - REST API handler
- `processPaymentCommand(orderId, amount)` - Message listener handler with idempotency
- `processPayment(orderId, amount)` - Core processing logic
- `processWithExternalGateway()` - Gateway invocation with error handling
- `choosePaymentGateway()` - Alternating gateway selection

### 5. **PaymentCommandListener Enhancement** (`listener/PaymentCommandListener.java`)

Implements RabbitMQ listener with:
- **Queue**: `q.payment.command`
- **Routing Key**: `payment.command.create` (from RabbitMQConstants)
- **Exchange**: `oms.exchange` (from RabbitMQConstants)

Enhanced features:
- Comprehensive input validation
- Detailed logging with emojis for easy tracking
- Error handling for malformed messages
- Delegates to PaymentService with idempotency support

### 6. **RabbitMQ Configuration** (`config/RabbitMqConfig.java`)
Already configured with:
- Exchange: `oms.exchange` (TopicExchange)
- Queue: `q.payment.command` (durable queue)
- Binding: Maps `payment.command.create` routing key to the queue
- Message converter: Jackson2JsonMessageConverter for JSON serialization

## Data Flow

```
Order Service (sends command)
    ↓
[RabbitMQ Exchange: oms.exchange]
    ↓
[Routing Key: payment.command.create]
    ↓
[Queue: q.payment.command]
    ↓
PaymentCommandListener (receives message)
    ↓
PaymentService.processPaymentCommand()
    ↓
[Idempotency Check] - Skip if already paid
    ↓
[Choose Gateway] - VNPay or Momo
    ↓
[2s Delay Simulation] - Network latency
    ↓
[Mock Payment Gateway] - 80-85% success rate
    ↓
[Update Payment Status] - COMPLETED or FAILED
    ↓
[RabbitMQ Exchange: oms.exchange]
    ↓
[Routing Key: payment.reply.result] ← Send result back
    ↓
Order Service (receives result)
```

## Database Schema Updates

The `payments` table now includes:
- `id` - Primary key
- `order_id` - Foreign key reference (unique constraint for idempotency)
- `amount` - Payment amount
- `status` - PENDING, COMPLETED, or FAILED
- `transaction_id` - Unique transaction identifier
- `payment_gateway` - Gateway used (VNPayGateway or MomoGateway)
- `retry_count` - Retry attempt counter
- `last_retry_at` - Timestamp of last retry
- `created_at` - Creation timestamp

## Testing Scenarios

### Scenario 1: Successful Payment (No Delay)
1. Order Service sends payment command
2. Gateway processes payment successfully (within 2s)
3. Payment status: COMPLETED
4. Result sent back via payment.reply.result

### Scenario 2: Failed Payment
1. Order Service sends payment command
2. Gateway fails payment (random 15-20% failure)
3. Payment status: FAILED
4. Result sent back via payment.reply.result

### Scenario 3: Duplicate Payment (Idempotency)
1. First payment command - processes normally
2. Duplicate payment command for same orderId
3. Service detects COMPLETED status
4. Skips reprocessing, sends existing result back
5. **Result**: No double-charging, maintains atomicity

### Scenario 4: Gateway Selection
- Payments processed alternately between VNPay and Momo
- Each gateway has distinct reference code prefix (VNP_ or MOMO_)

## Configuration
No additional configuration needed - uses existing RabbitMQ setup:
- Host: localhost (configurable via application.yml)
- Port: 5672
- Username/Password: configured in application.yml

## Common-Library Integration

Uses centralized RabbitMQ constants from `common-library`:
- `RabbitMQConstants.EXCHANGE_NAME` = "oms.exchange"
- `RabbitMQConstants.PAYMENT_COMMAND_CREATE` = "payment.command.create"
- `RabbitMQConstants.PAYMENT_REPLY_RESULT` = "payment.reply.result"

This ensures consistency across all microservices.

## Error Handling

1. **Invalid Payload** - Logs error, returns early
2. **Gateway Timeout** - Returns failed response
3. **Number Format Exception** - Logs and continues
4. **Unexpected Errors** - Wrapped in try-catch with detailed logging

## Logging

Enhanced logging with visual indicators:
- `🔔` - Received message
- `✅` - Successful operation
- `❌` - Error condition

Logs include:
- OrderId, Amount, TransactionId
- Gateway used and selection logic
- Payment status transitions
- Idempotency detection
- External gateway calls

## Dependencies

All required dependencies already in pom.xml:
- spring-boot-starter-amqp (RabbitMQ)
- spring-boot-starter-data-jpa (Database)
- spring-boot-starter-web (REST API)
- mariadb-java-client (Database driver)
- lombok (Annotations)
- common-library (Shared constants)
