package com.oms.paymentservice.service;

import lombok.extern.slf4j.Slf4j;
import com.oms.common.constant.RabbitMQConstants;
import com.oms.paymentservice.dto.PaymentRequest;
import com.oms.paymentservice.dto.PaymentResponse;
import com.oms.paymentservice.entity.Payment;
import com.oms.paymentservice.entity.PaymentStatus;
import com.oms.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;
    private final com.oms.paymentservice.config.VNPayConfig vnpayConfig;

    /**
     * API endpoint handler for payment requests
     */
    @Transactional
    public PaymentResponse pay(PaymentRequest request) {
        // Sinh URL trực tiếp cho API gọi từ Controller (nếu có)
        String transactionId = UUID.randomUUID().toString();
        String paymentUrl = generateVNPayUrl(request.orderId(), request.amount(), transactionId);
        
        Payment payment = paymentRepository.findByOrderId(request.orderId())
                .orElse(new Payment());
        
        payment.setOrderId(request.orderId());
        payment.setAmount(request.amount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId(transactionId);
        payment.setPaymentGateway("VNPAY");
        paymentRepository.save(payment);

        return new PaymentResponse(
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getTransactionId()
        );
    }

    /**
     * Message listener handler for payment command (Từ Nhạc trưởng SAGA)
     */
    @Transactional
    public void processPaymentCommand(String orderId, BigDecimal amount) {
        log.info("[VNPAY] Đang khởi tạo giao dịch cho đơn hàng: {}, Số tiền: {}", orderId, amount);
        
        // 1. Idempotency check
        var existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent() && existingPayment.get().getStatus() == PaymentStatus.COMPLETED) {
            log.warn("[VNPAY] Đơn hàng {} đã thanh toán rồi. Bỏ qua.", orderId);
            sendPaymentResult(orderId, PaymentStatus.COMPLETED.name(), existingPayment.get().getTransactionId());
            return;
        }

        // 2. Tạo Transaction ID và lưu DB trạng thái PENDING
        String transactionId = UUID.randomUUID().toString();
        Payment payment = existingPayment.orElse(new Payment());
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId(transactionId);
        payment.setPaymentGateway("VNPAY");
        paymentRepository.save(payment);

        // 3. Sinh URL thanh toán VNPay
        String paymentUrl = generateVNPayUrl(orderId, amount, transactionId);
        log.info("[VNPAY] Đã tạo URL thanh toán: {}", paymentUrl);

        // 4. Bắn event URL_CREATED về cho Nhạc trưởng để báo cho Order Service
        com.oms.common.dto.PaymentUrlCreatedEvent urlEvent = com.oms.common.dto.PaymentUrlCreatedEvent.builder()
                .orderId(orderId)
                .transactionId(transactionId)
                .paymentUrl(paymentUrl)
                .build();
        
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(
                        RabbitMQConstants.EXCHANGE_NAME,
                        RabbitMQConstants.PAYMENT_REPLY_URL_CREATED,
                        urlEvent
                );
                log.info("[VNPAY] Đã gửi thông báo URL_CREATED cho đơn hàng: {}", orderId);
            }
        });
    }

    private String generateVNPayUrl(String orderId, BigDecimal amount, String transactionId) {
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_OrderInfo = "Thanh toan don hang: " + orderId;
        String vnp_OrderType = "other";
        String vnp_TxnRef = transactionId;
        String vnp_IpAddr = "127.0.0.1";
        String vnp_TmnCode = vnpayConfig.getVnp_TmnCode();

        int amountInVnd = amount.multiply(new BigDecimal(100)).intValue();
        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amountInVnd));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", vnp_OrderInfo);
        vnp_Params.put("vnp_OrderType", vnp_OrderType);
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnpayConfig.getVnp_ReturnUrl());
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        // Ép múi giờ chuẩn Việt Nam
        TimeZone tz = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        Calendar cld = Calendar.getInstance(tz);
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(tz); // <--- Ép Formatter dùng giờ VN

        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List fieldNames = new ArrayList(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = (String) itr.next();
            String fieldValue = (String) vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(java.net.URLEncoder.encode(fieldValue, java.nio.charset.StandardCharsets.US_ASCII));
                // Build query
                query.append(java.net.URLEncoder.encode(fieldName, java.nio.charset.StandardCharsets.US_ASCII));
                query.append('=');
                query.append(java.net.URLEncoder.encode(fieldValue, java.nio.charset.StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        String queryUrl = query.toString();
        String vnp_SecureHash = vnpayConfig.hmacSHA512(vnpayConfig.getVnp_HashSecret(), hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        return vnpayConfig.getVnp_PayUrl() + "?" + queryUrl;
    }

    public void sendPaymentResult(String orderId, String status, String transactionId) {
        com.oms.paymentservice.dto.PaymentEvent event = new com.oms.paymentservice.dto.PaymentEvent(orderId, status, transactionId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(
                        RabbitMQConstants.EXCHANGE_NAME,
                        RabbitMQConstants.PAYMENT_REPLY_RESULT,
                        event
                );
                log.info("[VNPAY] Đã gửi kết quả thanh toán ({}) cho đơn hàng: {}", status, orderId);
            }
        });
    }
}
