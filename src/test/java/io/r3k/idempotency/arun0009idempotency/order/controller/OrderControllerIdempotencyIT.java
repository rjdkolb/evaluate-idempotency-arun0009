package io.r3k.idempotency.arun0009idempotency.order.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.r3k.idempotency.arun0009idempotency.TestcontainersConfiguration;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerIdempotencyIT {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/orders";
    }

    @Test
    void shouldReturnCachedResponseForDuplicateIdempotencyKey() {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {
                    "customerName": "Idempotent Corp",
                    "items": [
                        { "item": "MONITOR", "quantity": 1 },
                        { "item": "WEBCAM", "quantity": 2 }
                    ]
                }
                """;

        // First request — creates the order
        Response firstResponse = given().contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .when()
                .post()
                .then()
                .statusCode(201)
                .extract()
                .response();

        String firstRef = firstResponse.jsonPath().getString("orderReferenceNumber");

        // Second request — same key, same body — should return cached response
        given().contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .when()
                .post()
                .then()
                .statusCode(201)
                .body("orderReferenceNumber", equalTo(firstRef))
                .body("customerName", equalTo("Idempotent Corp"))
                .body("currency", equalTo("EUR"));
    }

    @Test
    void shouldCreateSeparateOrdersWithDifferentIdempotencyKeys() {
        String body1 = """
                {
                    "customerName": "Unique Corp",
                    "items": [
                        { "item": "HEADSET", "quantity": 1 }
                    ]
                }
                """;

        // First request with key A
        Response firstResponse = given().contentType(ContentType.JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(body1)
                .when()
                .post()
                .then()
                .statusCode(201)
                .extract()
                .response();

        String firstRef = firstResponse.jsonPath().getString("orderReferenceNumber");

        // Second request with key B and different body
        String body2 = """
                {
                    "customerName": "Unique Corp",
                    "items": [
                        { "item": "DESK_LAMP", "quantity": 2 }
                    ]
                }
                """;

        given().contentType(ContentType.JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(body2)
                .when()
                .post()
                .then()
                .statusCode(201)
                .body("orderReferenceNumber", not(equalTo(firstRef)));
    }

    @Test
    void shouldNotRequireIdempotencyKeyForGetRequests() {
        // GET requests should work without any Idempotency-Key header
        given().when().get().then().statusCode(200).body("$", instanceOf(java.util.List.class));
    }

    @Test
    void shouldReturnCachedResponseOnDuplicateSubmission() {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {
                    "customerName": "Duplicate Test Corp",
                    "items": [
                        { "item": "LAPTOP", "quantity": 1 }
                    ]
                }
                """;

        // First request — creates the order
        Response firstResponse = given().contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .when()
                .post()
                .then()
                .statusCode(201)
                .extract()
                .response();

        String firstRef = firstResponse.jsonPath().getString("orderReferenceNumber");

        // Second request with same idempotency key — should return cached response
        given().contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .when()
                .post()
                .then()
                .statusCode(201)
                .body("orderReferenceNumber", equalTo(firstRef));
    }
}
