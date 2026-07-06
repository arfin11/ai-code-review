package  com.arfin.code.review.kafka;
import com.arfin.code.review.model.PRReviewEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PRReviewProducer {

    private final KafkaTemplate<String, PRReviewEvent> kafkaTemplate;

    @Value("${topic.pr-review}")
    private String topic;

    public void publish(PRReviewEvent event) {

        try {
            SendResult<String, PRReviewEvent> result =
                    kafkaTemplate.send(topic, event.getRepo(), event).get();

            log.info("Message sent successfully.");
            log.info("Topic: {}", result.getRecordMetadata().topic());
            log.info("Partition: {}", result.getRecordMetadata().partition());
            log.info("Offset: {}", result.getRecordMetadata().offset());

        } catch (Exception e) {
            log.error("Failed to send message", e);
        }
    }
}