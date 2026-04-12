package io.r3k.idempotency.arun0009idempotency.order.dto;

import io.r3k.idempotency.arun0009idempotency.order.model.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Order creation request")
public record OrderRequest(
        @Schema(description = "Customer name", example = "Acme Corp")
        String customerName,

        @Schema(description = "Items to order") List<LineItemRequest> items) {
    @Schema(description = "A single item line in the order")
    public record LineItemRequest(
            @Schema(description = "Item to order", example = "LAPTOP")
            OrderItem item,

            @Schema(description = "Quantity", example = "1") int quantity) {}
}
