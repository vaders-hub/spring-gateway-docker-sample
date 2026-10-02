package com.example.backend.common.config;

import com.example.backend.common.config.properties.KafkaMessagingProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@Profile("kafka")
@EnableConfigurationProperties(KafkaMessagingProperties.class)
public class KafkaConfig {
    @Bean
    NewTopic orderEventsTopic(KafkaMessagingProperties properties) {
        return TopicBuilder.name(properties.orderTopic())
                .partitions(properties.partitions()).replicas(properties.replicas()).build();
    }

    @Bean
    NewTopic orderEventsDeadLetterTopic(KafkaMessagingProperties properties) {
        return TopicBuilder.name(properties.orderTopic() + ".DLT")
                .partitions(properties.partitions()).replicas(properties.replicas()).build();
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, error) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        // 잘못된 이벤트는 재시도해도 바뀌지 않는다. 일시적 처리 오류만 두 번 재시도한다.
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
