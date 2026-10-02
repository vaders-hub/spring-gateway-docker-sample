package com.example.backend.order.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("kafka")
public class OrderEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private final JsonMapper json;

    public OrderEventConsumer(JsonMapper json) { this.json = json; }

    @KafkaListener(id = "order-events", topics = "${app.messaging.kafka.order-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) {
        OrderEvent event;
        try {
            event = json.readValue(record.value(), OrderEvent.class);
            if (event == null || !event.orderId().toString().equals(record.key())) {
                throw new IllegalArgumentException("Invalid order event key");
            }
        } catch (RuntimeException error) {
            // 원문 JSON과 파서 오류 문자열을 로그·DLT 예외 헤더에 노출하지 않는다.
            throw new IllegalArgumentException("Invalid order event");
        }
        handle(event);
    }

    // 학습용 후속 처리 지점. DB 변경·외부 전송이 생기면 eventId 중복 처리 방지를 추가한다.
    void handle(OrderEvent event) {
        log.info("Kafka order event consumed eventId={} orderId={} type={} quantity={}",
                event.eventId(), event.orderId(), event.type(), event.quantity());
    }
}
