package com.oms.reporting.event;

import com.oms.common.dto.OrderCancelledEvent;
import com.oms.common.dto.OrderCompletedEvent;
import com.oms.reporting.entity.DailyRevenueStatistics;
import com.oms.reporting.entity.ProductSalesStatistics;
import com.oms.reporting.repository.DailyRevenueRepository;
import com.oms.reporting.repository.ProductSalesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final DailyRevenueRepository dailyRevenueRepository;
    private final ProductSalesRepository productSalesRepository;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "reporting.order.completed.queue", durable = "true"),
            exchange = @Exchange(value = "oms.exchange", type = "topic", ignoreDeclarationExceptions = "true"),
            key = "order.status.completed"
    ))
    @Transactional
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("Received OrderCompletedEvent for Order ID: {}", event.getOrderId());
        LocalDate today = LocalDate.now();
        
        DailyRevenueStatistics dailyStat = dailyRevenueRepository.findByStatDate(today)
                .orElse(DailyRevenueStatistics.builder()
                        .id(UUID.randomUUID().toString())
                        .statDate(today)
                        .build());
        
        dailyStat.setTotalRevenue(dailyStat.getTotalRevenue().add(event.getTotalAmount()));
        if ("COD".equalsIgnoreCase(event.getPaymentMethod())) {
            dailyStat.setCodRevenue(dailyStat.getCodRevenue().add(event.getTotalAmount()));
        } else {
            dailyStat.setOnlineRevenue(dailyStat.getOnlineRevenue().add(event.getTotalAmount()));
        }
        dailyStat.setCompletedOrders(dailyStat.getCompletedOrders() + 1);
        
        dailyRevenueRepository.save(dailyStat);

        // Update Product Sales
        if (event.getItems() != null) {
            for (OrderCompletedEvent.OrderItem item : event.getItems()) {
                ProductSalesStatistics productStat = productSalesRepository.findByProductId(item.getProductId())
                        .orElse(ProductSalesStatistics.builder()
                                .id(UUID.randomUUID().toString())
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .build());
                
                productStat.setTotalSoldQuantity(productStat.getTotalSoldQuantity() + item.getQuantity());
                productStat.setTotalRevenue(productStat.getTotalRevenue().add(item.getPrice()));
                productStat.setLastUpdatedAt(LocalDateTime.now());
                
                productSalesRepository.save(productStat);
            }
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "reporting.order.cancelled.queue", durable = "true"),
            exchange = @Exchange(value = "oms.exchange", type = "topic", ignoreDeclarationExceptions = "true"),
            key = "order.status.cancelled"
    ))
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelledEvent for Order ID: {}", event.getOrderId());
        
        LocalDate cancelledDate = event.getCancelledAt() != null ? event.getCancelledAt().toLocalDate() : LocalDate.now();
        
        DailyRevenueStatistics dailyStat = dailyRevenueRepository.findByStatDate(cancelledDate)
                .orElse(DailyRevenueStatistics.builder()
                        .id(UUID.randomUUID().toString())
                        .statDate(cancelledDate)
                        .build());
        
        dailyStat.setCancelledOrders(dailyStat.getCancelledOrders() + 1);
        
        dailyRevenueRepository.save(dailyStat);
    }
}
