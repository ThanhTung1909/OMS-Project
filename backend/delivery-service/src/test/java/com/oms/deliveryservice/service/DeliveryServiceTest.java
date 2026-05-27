package com.oms.deliveryservice.service;

import com.oms.common.enums.DeliveryStatus;
import com.oms.deliveryservice.dto.DeliveryCommand;
import com.oms.deliveryservice.entity.Delivery;
import com.oms.deliveryservice.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private DeliveryService deliveryService;

    private DeliveryCommand command;
    private Delivery existingDelivery;

    @BeforeEach
    public void setUp() {
        command = new DeliveryCommand();
        command.setOrderId("ORD-001");
        command.setReceiverName("Nguyen Van B");
        command.setReceiverPhone("0987654321");
        command.setAddress("123 Duong Le Loi, Q1, HCM");
        command.setCodAmount(new BigDecimal("150000"));

        existingDelivery = Delivery.builder()
                .id("DEL-001")
                .orderId("ORD-001")
                .trackingNumber("OMS-SHIP-TEST")
                .status(DeliveryStatus.READY_TO_UP)
                .shipperId("shipper_demo")
                .shipperName("Shipper Demo")
                .shipperPhone("0988888888")
                .receiverName("Nguyen Van B")
                .receiverPhone("0987654321")
                .address("123 Duong Le Loi, Q1, HCM")
                .codAmount(new BigDecimal("150000"))
                .build();
    }

    @Test
    public void testCreateDelivery_Success() {
        Mockito.when(deliveryRepository.save(any(Delivery.class)))
                .thenAnswer(invocation -> {
                    Delivery d = invocation.getArgument(0);
                    d.setId("DEL-001");
                    return d;
                });

        Delivery result = deliveryService.createDelivery(command);

        assertNotNull(result);
        assertEquals("ORD-001", result.getOrderId());
        assertEquals("shipper_demo", result.getShipperId());
        assertEquals("Shipper Demo", result.getShipperName());
        assertEquals(DeliveryStatus.READY_TO_UP, result.getStatus());

        Mockito.verify(deliveryRepository, Mockito.times(1)).save(any(Delivery.class));
        Mockito.verify(rabbitTemplate, Mockito.times(1)).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    public void testGetDeliveriesByShipper_Success() {
        List<Delivery> deliveries = new ArrayList<>();
        deliveries.add(existingDelivery);

        Mockito.when(deliveryRepository.findByShipperId(eq("shipper_demo")))
                .thenReturn(deliveries);

        List<Delivery> result = deliveryService.getDeliveriesByShipper("shipper_demo", null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("shipper_demo", result.get(0).getShipperId());
    }

    @Test
    public void testUpdateStatusForShipper_Success() {
        Mockito.when(deliveryRepository.findById(eq("DEL-001")))
                .thenReturn(Optional.of(existingDelivery));
        Mockito.when(deliveryRepository.save(any(Delivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Delivery> result = deliveryService.updateStatusForShipper(
                "DEL-001", "shipper_demo", DeliveryStatus.DELIVERING, null);

        assertTrue(result.isPresent());
        assertEquals(DeliveryStatus.DELIVERING, result.get().getStatus());
        Mockito.verify(deliveryRepository, Mockito.times(1)).save(existingDelivery);
    }

    @Test
    public void testUpdateStatusForShipper_Forbidden() {
        Mockito.when(deliveryRepository.findById(eq("DEL-001")))
                .thenReturn(Optional.of(existingDelivery));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            deliveryService.updateStatusForShipper("DEL-001", "other_shipper", DeliveryStatus.DELIVERING, null);
        });

        assertEquals("Bạn không có quyền cập nhật trạng thái đơn vận chuyển này!", exception.getMessage());
        Mockito.verify(deliveryRepository, Mockito.never()).save(any(Delivery.class));
    }
}
