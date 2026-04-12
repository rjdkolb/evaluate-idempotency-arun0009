package io.r3k.idempotency.arun0009idempotency.order.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.r3k.idempotency.arun0009idempotency.TestcontainersConfiguration;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerIT {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/orders";
    }

    @Test
    void shouldCreateOrderAndCalculateTotals() {
        // LAPTOP = 999.99, MOUSE = 25.99
        // subtotal = 999.99 * 1 + 25.99 * 2 = 999.99 + 51.98 = 1051.97
        // vat = 1051.97 * 0.21 = 220.91 (HALF_UP)
        // total = 1051.97 + 220.91 = 1272.88
        String body = """
                {
                    "customerName": "Acme Corp",
                    "items": [
                        { "item": "LAPTOP", "quantity": 1 },
                        { "item": "MOUSE", "quantity": 2 }
                    ]
                }
                """;

        given().contentType(ContentType.JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(body)
                .when()
                .post()
                .then()
                .statusCode(201)
                .body("orderReferenceNumber", startsWith("ORD-"))
                .body("customerName", equalTo("Acme Corp"))
                .body("currency", equalTo("EUR"))
                .body("status", equalTo("PENDING"))
                .body("subtotal", equalTo(1051.97f))
                .body("vatRate", equalTo(0.21f))
                .body("vatAmount", equalTo(220.91f))
                .body("totalPayable", equalTo(1272.88f))
                .body("items", hasSize(2))
                .body("items[0].item", equalTo("LAPTOP"))
                .body("items[0].quantity", equalTo(1))
                .body("items[0].unitPrice", equalTo(999.99f))
                .body("items[0].lineTotal", equalTo(999.99f))
                .body("items[1].item", equalTo("MOUSE"))
                .body("items[1].quantity", equalTo(2))
                .body("items[1].unitPrice", equalTo(25.99f))
                .body("items[1].lineTotal", equalTo(51.98f));
    }

    @Test
    void shouldCreateOrderWithSingleItem() {
        // KEYBOARD = 79.99
        // subtotal = 79.99 * 3 = 239.97
        // vat = 239.97 * 0.21 = 50.39 (HALF_UP)
        // total = 239.97 + 50.39 = 290.36
        String body = """
                {
                    "customerName": "Widget Inc",
                    "items": [
                        { "item": "KEYBOARD", "quantity": 3 }
                    ]
                }
                """;

        given().contentType(ContentType.JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(body)
                .when()
                .post()
                .then()
                .statusCode(201)
                .body("orderReferenceNumber", startsWith("ORD-"))
                .body("subtotal", equalTo(239.97f))
                .body("vatAmount", equalTo(50.39f))
                .body("totalPayable", equalTo(290.36f))
                .body("currency", equalTo("EUR"));
    }

    @Test
    void shouldListOrders() {
        // Create an order first
        String body = """
                {
                    "customerName": "List Test Corp",
                    "items": [
                        { "item": "USB_CABLE", "quantity": 1 }
                    ]
                }
                """;

        given().contentType(ContentType.JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(body)
                .when()
                .post()
                .then()
                .statusCode(201);

        // List all orders
        given().when()
                .get()
                .then()
                .statusCode(200)
                .body("$", instanceOf(java.util.List.class))
                .body("size()", greaterThanOrEqualTo(1));
    }
}
