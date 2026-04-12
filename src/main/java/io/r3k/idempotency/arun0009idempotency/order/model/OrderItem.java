package io.r3k.idempotency.arun0009idempotency.order.model;

import java.math.BigDecimal;

public enum OrderItem {
    LAPTOP(new BigDecimal("999.99")),
    MOUSE(new BigDecimal("25.99")),
    KEYBOARD(new BigDecimal("79.99")),
    MONITOR(new BigDecimal("349.99")),
    HEADSET(new BigDecimal("89.99")),
    USB_CABLE(new BigDecimal("9.99")),
    WEBCAM(new BigDecimal("64.99")),
    DESK_LAMP(new BigDecimal("44.99"));

    private final BigDecimal price;

    OrderItem(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
