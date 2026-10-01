package com.example.backend.order.repository.jpa;

import com.example.backend.order.repository.OrderPersistenceContractTest;
import org.springframework.test.context.ActiveProfiles;

// 실제 운영 DB 대신 일회용 PostgreSQL에서 기존 prod JPA 경로를 검증한다.
@ActiveProfiles("prod")
class OrderPersistenceIntegrationTest extends OrderPersistenceContractTest {}
