package io.r3k.idempotency.arun0009idempotency.order.repository;

import io.r3k.idempotency.arun0009idempotency.order.model.Order;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {}
