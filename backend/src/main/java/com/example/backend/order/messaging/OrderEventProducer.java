package com.example.backend.order.messaging;

import com.example.backend.common.config.properties.KafkaMessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("kafka")
public class OrderEventProducer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final KafkaMessagingProperties properties;

    public OrderEventProducer(KafkaTemplate<String, String> kafka, JsonMapper json, KafkaMessagingProperties properties) {
        this.kafka = kafka;
        this.json = json;
        this.properties = properties;
    }

    // DB rollback에는 발행하지 않는다. 트랜잭션이 없는 메모리 모드에서는 즉시 발행한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderEvent(OrderEvent event) {
        try {
            kafka.send(properties.orderTopic(), event.orderId().toString(), json.writeValueAsString(event))
                    .whenComplete((result, error) -> {
                        if (error == null) {
                            log.info("Kafka order event published eventId={} orderId={} type={}",
                                    event.eventId(), event.orderId(), event.type());
                        } else {
                            logFailure(event, error);
                        }
                    });
        } catch (RuntimeException error) {
            // 이미 완료된 DB commit을 되돌릴 수 없다. 자동 재발행은 Outbox 후속 단계다.
            logFailure(event, error);
        }
    }

    private void logFailure(OrderEvent event, Throwable error) {
        log.error("Kafka order event publish failed eventId={} orderId={} type={} errorType={}",
                event.eventId(), event.orderId(), event.type(), error.getClass().getSimpleName());
    }
}
