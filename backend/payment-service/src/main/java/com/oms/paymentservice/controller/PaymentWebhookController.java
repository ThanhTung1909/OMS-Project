package com.oms.paymentservice.controller;

import com.oms.paymentservice.config.VNPayConfig;
import com.oms.paymentservice.entity.Payment;
import com.oms.paymentservice.entity.PaymentStatus;
import com.oms.paymentservice.repository.PaymentRepository;
import com.oms.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final VNPayConfig vnpayConfig;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    /**
     * API này dùng cho VNPay gọi vào (IPN - Instant Payment Notification)
     */
    @GetMapping("/vnpay-ipn")
    @Transactional
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> queryParams) {
        log.info("[VNPAY WEBHOOK] Nhận được IPN call từ VNPay với params: {}", queryParams);
        Map<String, String> response = new HashMap<>();

        try {
            // 1. Xác thực chữ ký (Checksum) chống giả mạo
            String vnp_SecureHash = queryParams.get("vnp_SecureHash");
            
            Map<String, String> hashParams = new HashMap<>(queryParams);
            hashParams.remove("vnp_SecureHash");
            hashParams.remove("vnp_SecureHashType");

            String signValue = vnpayConfig.hashAllFields(hashParams);
            
            if (!signValue.equals(vnp_SecureHash)) {
                log.error("[VNPAY WEBHOOK] Sai chữ ký (Invalid Signature)! Expected: {}, Got: {}", signValue, vnp_SecureHash);
                response.put("RspCode", "97");
                response.put("Message", "Invalid Checksum");
                return ResponseEntity.ok(response);
            }

            // 2. Lấy thông tin giao dịch
            String transactionId = queryParams.get("vnp_TxnRef");
            String responseCode = queryParams.get("vnp_ResponseCode");

            Payment payment = paymentRepository.findByTransactionId(transactionId).orElse(null);
            if (payment == null) {
                log.error("[VNPAY WEBHOOK] Không tìm thấy giao dịch: {}", transactionId);
                response.put("RspCode", "01");
                response.put("Message", "Order not found");
                return ResponseEntity.ok(response);
            }

            // 3. Kiểm tra Idempotency (Tránh VNPay gọi 2 lần cho 1 đơn)
            if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.FAILED) {
                log.info("[VNPAY WEBHOOK] Giao dịch {} đã được xử lý trước đó. Bỏ qua.", transactionId);
                response.put("RspCode", "02");
                response.put("Message", "Order already confirmed");
                return ResponseEntity.ok(response);
            }

            // 4. Cập nhật trạng thái và ĐÁNH THỨC SAGA ORCHESTRATOR
            if ("00".equals(responseCode)) {
                payment.setStatus(PaymentStatus.COMPLETED);
                log.info("[VNPAY WEBHOOK] Giao dịch {} THÀNH CÔNG!", transactionId);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                log.warn("[VNPAY WEBHOOK] Giao dịch {} THẤT BẠI. Mã lỗi: {}", transactionId, responseCode);
            }
            paymentRepository.save(payment);

            // BẮN SAGA REPLY (Chốt hạ)
            paymentService.sendPaymentResult(payment.getOrderId(), payment.getStatus().name(), transactionId);

            // Trả về cho VNPay biết mình đã ghi nhận
            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[VNPAY WEBHOOK] Lỗi xử lý IPN: {}", e.getMessage(), e);
            response.put("RspCode", "99");
            response.put("Message", "Unknown error");
            return ResponseEntity.ok(response);
        }
    }
}
