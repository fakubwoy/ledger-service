package com.fakubwoy.ledger.outbox;

import com.fakubwoy.ledger.transfer.TransferCompleted;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
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
	private final Tracer tracer = GlobalOpenTelemetry.getTracer("ledger.outbox");

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
		var sends = batch.stream().map(this::send).toList();
		for (var send : sends) {
			send.get(10, TimeUnit.SECONDS);
		}
		batch.forEach(OutboxMessage::markPublished);
	}

	// The span is parented on the request that wrote the row, so the Kafka send shows up in that request's trace.
	private java.util.concurrent.CompletableFuture<?> send(OutboxMessage m) {
		var span = tracer.spanBuilder("outbox publish").setParent(m.originContext()).startSpan();
		try (var scope = span.makeCurrent()) {
			return kafka.send(TransferCompleted.TOPIC, m.getAggregateId().toString(), m.getPayload());
		} finally {
			span.end();
		}
	}
}
