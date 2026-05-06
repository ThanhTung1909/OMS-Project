# Payment Service - RabbitMQ Communication Reference

## Routing Keys (from common-library/RabbitMQConstants)

### Command Flow (Order → Payment)
```
Exchange: oms.exchange
Routing Key: payment.command.create
Queue: q.payment.command
Payload Example:
{
  "orderId": "ORD-2025-001",
  "amount": 100.50
}
```

### Reply Flow (Payment → Order)
```
Exchange: oms.exchange
Routing Key: payment.reply.result
Payload Example:
{
  "orderId": "ORD-2025-001",
  "paymentStatus": "COMPLETED|FAILED",
  "transactionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

## Payment Processing Sequence

1. **Order Service** sends payment command
   - Exchange: `oms.exchange`
   - Routing Key: `payment.command.create`
   - Contains: orderId, amount

2. **PaymentCommandListener** receives the command
   - Validates payload
   - Calls `PaymentService.processPaymentCommand(orderId, amount)`

3. **PaymentService** processes payment
   - Checks idempotency (already paid?)
   - Chooses gateway (VNPay or Momo)
   - Calls gateway with 2s delay
   - Saves payment to database
   - Sends result back

4. **RabbitTemplate** sends result
   - Exchange: `oms.exchange`
   - Routing Key: `payment.reply.result`
   - Payload: PaymentEvent (orderId, paymentStatus, transactionId)

5. **Order Service** receives result
   - Updates order status based on payment result
   - Continues order processing

## Gateway Selection Logic

Payment gateway is chosen using alternation:
```
count = paymentRepository.count()
gateway = (count % 2 == 0) ? VNPayGateway : MomoGateway
```

This means:
- 1st payment → VNPay
- 2nd payment → Momo
- 3rd payment → VNPay
- And so on...

## Idempotency Implementation

Before processing a payment, the system checks:
```java
var existingPayment = paymentRepository.findByOrderId(orderId);
if (existingPayment.isPresent() && 
    existingPayment.get().getStatus() == PaymentStatus.COMPLETED) {
    // Skip processing, return existing result
}
```

**Key Benefits:**
- Prevents double-charging
- Handles duplicate messages from message broker
- Maintains data consistency
- Returns same result for same orderId

## Mock Gateway Behavior

### VNPayGateway
- Simulates: 2000ms (2 seconds)
- Success Rate: 80%
- Reference Code Prefix: `VNP_`
- Example: `VNP_a1b2c3d4`

### MomoGateway  
- Simulates: 2000ms (2 seconds)
- Success Rate: 85%
- Reference Code Prefix: `MOMO_`
- Example: `MOMO_x9y8z7w6`

## Testing Idempotency

```
Step 1: Send payment command for Order-123
→ Status: PENDING → COMPLETED
→ Gateway called, 2s delay

Step 2: Send SAME payment command for Order-123  
→ Status: Already COMPLETED (detected)
→ Gateway NOT called, instant reply
→ Returns same transactionId

Result: Double payment prevented ✓
```

## Database Schema

### payments table
- `id` - Auto-increment primary key
- `order_id` - Unique (enforces idempotency)
- `amount` - Payment amount
- `status` - PENDING|COMPLETED|FAILED
- `transaction_id` - Unique identifier
- `payment_gateway` - VNPayGateway|MomoGateway
- `retry_count` - Number of attempts
- `last_retry_at` - Timestamp of last attempt
- `created_at` - Creation timestamp

## Logging Output Example

```
🔔 [PAYMENT] Received payment command from Order Service: {orderId=ORD-2025-001, amount=100.50}
✅ Payment command validated - OrderId: ORD-2025-001, Amount: 100.50
Processing payment via VNPay - OrderId: ORD-2025-001, Amount: 100.50, TransactionId: 550e8400-e29b-41d4-a716-446655440000
VNPay payment successful - ReferenceCode: VNP_a1b2c3d4
✅ Payment processing completed - OrderId: ORD-2025-001, Status: COMPLETED, Gateway: VNPayGateway
✅ Payment command processed successfully for OrderId: ORD-2025-001
```

## Error Scenarios

### Missing/Invalid Payload
```
❌ Invalid payment command payload - missing orderId or amount: {...}
→ Message is skipped
```

### Negative Amount
```
❌ Payment amount must be greater than 0
→ Message is skipped
```

### Gateway Failure
```
External gateway error for orderId: ORD-2025-001: Connection timeout
→ Status: FAILED
→ Result sent back: paymentStatus = "FAILED"
```

### Duplicate Payment (Idempotency)
```
✅ Payment already completed for orderId: ORD-2025-001. Skipping duplicate payment processing.
→ Sends existing result
→ No database transaction
```
