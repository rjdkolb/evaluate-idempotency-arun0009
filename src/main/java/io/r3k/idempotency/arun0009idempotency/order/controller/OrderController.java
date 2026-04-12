package io.r3k.idempotency.arun0009idempotency.order.controller;

import io.github.arun0009.idempotent.core.annotation.Idempotent;
import io.r3k.idempotency.arun0009idempotency.order.dto.OrderRequest;
import io.r3k.idempotency.arun0009idempotency.order.dto.OrderResponse;
import io.r3k.idempotency.arun0009idempotency.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management with idempotency support")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent(key = "#request", duration = "PT1H", hashKey = true)
    @Operation(
            summary = "Create an order",
            description = """
                    Creates a new order and calculates the total including 21% VAT.

                    **Idempotency**: Send the same `Idempotency-Key` header to safely retry — \
                    the server returns the cached response without creating a duplicate order.

                    **Delay**: Use the `delay` query parameter to simulate a slow request \
                    (useful for testing idempotency by submitting the same key in parallel).""",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = OrderRequest.class),
                                            examples = @ExampleObject(name = "Sample order", value = """
                                            {
                                                "customerName": "Acme Corp",
                                                "items": [
                                                    { "item": "LAPTOP", "quantity": 1 },
                                                    { "item": "MOUSE", "quantity": 2 }
                                                ]
                                            }"""))),
            responses = @ApiResponse(responseCode = "201", description = "Order created"))
    public OrderResponse createOrder(
            @RequestBody OrderRequest request,
            @Parameter(
                            description =
                                    "Optional delay in seconds (simulates slow processing for idempotency testing)",
                            example = "1")
                    @RequestParam(required = false, defaultValue = "1")
                    int delay) {

        sleep(delay);

        return orderService.createOrder(request);
    }

    @SneakyThrows
    private static void sleep(int delay) {

        Thread.sleep(delay * 1000L);
    }

    @GetMapping
    @Operation(summary = "List all orders", description = "Returns all orders in the system.")
    public List<OrderResponse> listOrders() {
        return orderService.listOrders();
    }
}
