package com.fakubwoy.ledger.outbox;

import com.fakubwoy.ledger.transfer.TransferCompleted;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

	private final OutboxRepository outbox;
	private final KafkaTemplate<String, String> kafka;

	public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
		this.outbox = outbox;
		this.kafka = kafka;
	}

	@Bean
	static NewTopic transferCompletedTopic() {
		return TopicBuilder.name(TransferCompleted.TOPIC).partitions(3).replicas(1).build();
	}

	/**
	 * At-least-once: if we crash after Kafka acks but before commit, the batch is sent again,
	 * so consumers must be idempotent. Any send failure rolls back and the rows are retried next tick.
	 */
	@Scheduled(fixedDelayString = "${ledger.outbox.poll-interval-ms}")
	@Transactional
	public void publish() throws Exception {
		var batch = outbox.lockUnpublished(100);
		var sends = batch.stream()
				.map(m -> kafka.send(TransferCompleted.TOPIC, m.getAggregateId().toString(), m.getPayload()))
				.toList();
		for (var send : sends) {
			send.get(10, TimeUnit.SECONDS);
		}
		batch.forEach(OutboxMessage::markPublished);
	}
}
