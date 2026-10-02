package com.example.backend.order.messaging;

import com.example.backend.common.config.properties.KafkaMessagingProperties;
import com.example.backend.order.service.OrderService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 기본 test와 분리한다. 프로젝트의 Compose/DB/볼륨을 건드리지 않는 일회용 브로커다.
@Tag("kafka")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "kafka"})
@Import(OrderKafkaIntegrationTest.TransactionEventTestConfig.class)
class OrderKafkaIntegrationTest {
    @Container
    static final KafkaContainer broker = new KafkaContainer("apache/kafka:4.2.2");

    private static final String TOPIC = "test.orders." + UUID.randomUUID();
    private static final String GROUP = "test.backend." + UUID.randomUUID();
    private static final String SECRET = UUID.randomUUID().toString();
    private final BlockingQueue<OrderEvent> received = new LinkedBlockingQueue<>();

    @Autowired OrderService orders;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired KafkaMessagingProperties properties;
    @Autowired ApplicationEventPublisher events;
    @Autowired JsonMapper json;
    @MockitoSpyBean OrderEventConsumer consumer;
    @MockitoSpyBean OrderEventProducer producer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", broker::getBootstrapServers);
        registry.add("app.messaging.kafka.order-topic", () -> TOPIC);
        registry.add("spring.kafka.consumer.group-id", () -> GROUP);
        registry.add("JWT_ISSUER", () -> "kafka-test");
        registry.add("JWT_SECRET", () -> SECRET);
        registry.add("JWT_AUDIENCE", () -> "kafka-test-api");
    }

    @BeforeEach
    void observeSuccessfulHandling() {
        doAnswer(call -> {
            call.callRealMethod();
            received.add(call.getArgument(0));
            return null;
        }).when(consumer).handle(any());
    }

    @Test
    void memoryOrderMutationsPublishAndConsumeThreeEventsInOrder() throws Exception {
        var order = orders.place(1, 1, 2, "kafka-test-owner");
        orders.updateQuantity(order.id(), 3, "kafka-test-owner");
        orders.delete(order.id(), "kafka-test-owner");
        var created = next();
        var updated = next();
        var deleted = next();
        assertThat(List.of(created.type(), updated.type(), deleted.type()))
                .containsExactly(OrderEvent.Type.CREATED, OrderEvent.Type.QUANTITY_UPDATED, OrderEvent.Type.DELETED);
        assertThat(List.of(created.orderId(), updated.orderId(), deleted.orderId())).containsOnly(order.id());
        assertThat(created.quantity()).isEqualTo(2);
        assertThat(updated.quantity()).isEqualTo(3);
        assertThat(deleted.quantity()).isNull();
        assertThat(List.of(created.eventId(), updated.eventId(), deleted.eventId())).doesNotHaveDuplicates();
        assertThat(json.writeValueAsString(created)).doesNotContain("kafka-test-owner");
    }

    @Test
    void transactionEventsWaitForCommitAndAreDiscardedOnRollback() throws Exception {
        // 실제 Spring 트랜잭션 동기화를 사용한다. DB 저장 rollback은 databaseTest에서 따로 검증한다.
        var transaction = new TransactionTemplate(new SynchronizationOnlyTransactionManager());
        var committed = event();
        transaction.executeWithoutResult(status -> {
            events.publishEvent(committed);
            verify(producer, times(0)).onOrderEvent(any());
        });
        assertThat(next()).isEqualTo(committed);

        var rolledBack = event();
        transaction.executeWithoutResult(status -> {
            events.publishEvent(rolledBack);
            status.setRollbackOnly();
        });
        // 같은 파티션의 뒤따르는 정상 이벤트가 도착하면 앞선 rollback 이벤트의 부재를 판정한다.
        var sentinel = new OrderEvent(UUID.randomUUID(), rolledBack.orderId(),
                OrderEvent.Type.QUANTITY_UPDATED, 2, Instant.now());
        events.publishEvent(sentinel);
        assertThat(next()).isEqualTo(sentinel);
        verify(consumer, times(0)).handle(argThat(e -> e.eventId().equals(rolledBack.eventId())));
    }

    @Test
    void transientHandlerFailureIsRetriedTwiceThenSucceeds() throws Exception {
        var event = event();
        var attempts = new AtomicInteger();
        doAnswer(call -> {
            OrderEvent current = call.getArgument(0);
            if (current.eventId().equals(event.eventId()) && attempts.incrementAndGet() <= 2) {
                throw new IllegalStateException("Simulated transient failure");
            }
            call.callRealMethod();
            received.add(current);
            return null;
        }).when(consumer).handle(any());
        kafka.send(TOPIC, event.orderId().toString(), json.writeValueAsString(event)).get(15, TimeUnit.SECONDS);
        assertThat(next()).isEqualTo(event);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void invalidJsonIsSentToDeadLetterTopicAndFollowingRecordsContinue() throws Exception {
        assertDeadLetter("invalid-key", "{broken", false);
    }

    @Test
    void exhaustedHandlerFailureIsSentToDeadLetterTopic() throws Exception {
        var event = event();
        doAnswer(call -> { throw new IllegalStateException("Simulated persistent failure"); })
                .when(consumer).handle(argThat(e -> e.eventId().equals(event.eventId())));
        assertDeadLetter(event.orderId().toString(), json.writeValueAsString(event), true);
        verify(consumer, times(3)).handle(argThat(e -> e.eventId().equals(event.eventId())));
    }

    private void assertDeadLetter(String key, String payload, boolean validEvent) throws Exception {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-observer." + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var dlt = new KafkaConsumer<String, String>(config)) {
            var partition = new TopicPartition(properties.orderTopic() + ".DLT", 0);
            dlt.assign(List.of(partition));
            dlt.seekToBeginning(List.of(partition));
            kafka.send(TOPIC, key, payload).get(15, TimeUnit.SECONDS);
            boolean found = false;
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (!found && System.nanoTime() < deadline) {
                for (var record : dlt.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key()) && payload.equals(record.value())) found = true;
                }
            }
            assertThat(found).as("Failed message recovered to DLT").isTrue();
            if (!validEvent) verify(consumer, times(0)).handle(any());
        }
        var following = event();
        events.publishEvent(following);
        assertThat(next()).isEqualTo(following);
    }

    private OrderEvent next() throws InterruptedException {
        var event = received.poll(30, TimeUnit.SECONDS);
        assertThat(event).as("Kafka listener handled an event").isNotNull();
        return event;
    }

    private static OrderEvent event() {
        return new OrderEvent(UUID.randomUUID(), UUID.randomUUID(), OrderEvent.Type.CREATED, 1, Instant.now());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransactionEventTestConfig {
        // 메모리 프로필에는 트랜잭션 자동 구성이 없다. 수동 트랜잭션 검증에 필요한
        // 리스너 factory만 추가하고 서비스에 DB 트랜잭션 매니저를 주입하지 않는다.
        @Bean
        static TransactionalEventListenerFactory transactionEventListenerFactory() {
            return new TransactionalEventListenerFactory();
        }
    }

    private static final class SynchronizationOnlyTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
