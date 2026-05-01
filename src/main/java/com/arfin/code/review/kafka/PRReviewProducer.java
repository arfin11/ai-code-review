package com.arfin.code.review.kafka;

import com.arfin.code.review.model.PRReviewEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Data
@RequiredArgsConstructor
public class PRReviewProducer {

    private final KafkaTemplate<String, PRReviewEvent> kafkaTemplate;

    @Value("${topic.pr-review}")
    private String topic;

    public void publish(PRReviewEvent event) {
        kafkaTemplate.send(topic, event.getRepo(), event);
    }
}