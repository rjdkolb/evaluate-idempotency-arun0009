package io.r3k.idempotency.arun0009idempotency.order.dto;

import io.r3k.idempotency.arun0009idempotency.order.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Order response with pricing breakdown")
public record OrderResponse(
        @Schema(description = "Unique order reference number", example = "ORD-A1B2C3D4")
        String orderReferenceNumber,

        @Schema(description = "Customer name", example = "Acme Corp")
        String customerName,

        @Schema(description = "Ordered items with pricing") List<LineItemResponse> items,

        @Schema(description = "Subtotal before VAT", example = "1051.97")
        BigDecimal subtotal,

        @Schema(description = "VAT rate applied", example = "0.21")
        BigDecimal vatRate,

        @Schema(description = "VAT amount", example = "220.91")
        BigDecimal vatAmount,

        @Schema(description = "Total payable amount including VAT", example = "1272.88")
        BigDecimal totalPayable,

        @Schema(description = "Currency code", example = "EUR")
        String currency,

        OrderStatus status,
        LocalDateTime createdAt) {
    public record LineItemResponse(
            @Schema(description = "Item name", example = "LAPTOP")
            String item,

            @Schema(description = "Quantity ordered", example = "1")
            int quantity,

            @Schema(description = "Unit price", example = "999.99")
            BigDecimal unitPrice,

            @Schema(description = "Line total (quantity × unit price)", example = "999.99")
            BigDecimal lineTotal) {}
}
