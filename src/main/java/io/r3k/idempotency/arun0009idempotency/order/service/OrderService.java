package io.r3k.idempotency.arun0009idempotency.order.service;

import io.r3k.idempotency.arun0009idempotency.order.dto.OrderRequest;
import io.r3k.idempotency.arun0009idempotency.order.dto.OrderResponse;
import io.r3k.idempotency.arun0009idempotency.order.model.Order;
import io.r3k.idempotency.arun0009idempotency.order.model.OrderLineItem;
import io.r3k.idempotency.arun0009idempotency.order.model.OrderStatus;
import io.r3k.idempotency.arun0009idempotency.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.21");
    private static final int SCALE = EUR.getDefaultFractionDigits();

    private final OrderRepository orderRepository;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Order order = Order.builder()
                .orderReferenceNumber(generateReferenceNumber())
                .customerName(request.customerName())
                .currency(EUR.getCurrencyCode())
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .subtotal(BigDecimal.ZERO)
                .vatAmount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderRequest.LineItemRequest itemRequest : request.items()) {
            BigDecimal unitPrice = itemRequest.item().getPrice().setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice
                    .multiply(BigDecimal.valueOf(itemRequest.quantity()))
                    .setScale(SCALE, RoundingMode.HALF_UP);

            OrderLineItem lineItem = OrderLineItem.builder()
                    .item(itemRequest.item())
                    .quantity(itemRequest.quantity())
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build();

            order.addLineItem(lineItem);
            subtotal = subtotal.add(lineTotal);
        }

        subtotal = subtotal.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal vatAmount = subtotal.multiply(VAT_RATE).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(vatAmount).setScale(SCALE, RoundingMode.HALF_UP);

        order.setSubtotal(subtotal);
        order.setVatAmount(vatAmount);
        order.setTotal(total);

        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders() {
        return orderRepository.findAll().stream().map(this::toResponse).toList();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderResponse.LineItemResponse> items = order.getLineItems().stream()
                .map(li -> new OrderResponse.LineItemResponse(
                        li.getItem().name(), li.getQuantity(), li.getUnitPrice(), li.getLineTotal()))
                .toList();

        return new OrderResponse(
                order.getOrderReferenceNumber(),
                order.getCustomerName(),
                items,
                order.getSubtotal(),
                VAT_RATE,
                order.getVatAmount(),
                order.getTotal(),
                order.getCurrency(),
                order.getStatus(),
                order.getCreatedAt());
    }

    private String generateReferenceNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
