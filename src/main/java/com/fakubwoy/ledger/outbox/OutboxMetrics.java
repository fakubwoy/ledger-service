package com.fakubwoy.ledger.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

// Rows written but not yet published to Kafka. A backlog that only grows means publishing is stuck.
@Component
class OutboxMetrics {

	OutboxMetrics(MeterRegistry meters, OutboxRepository outbox) {
		// Cheap: backed by the partial index on unpublished rows. Every pod reports the same table-wide number.
		Gauge.builder("ledger.outbox.backlog", outbox, OutboxRepository::countByPublishedAtIsNull)
				.description("Outbox rows not yet published to Kafka").register(meters);
	}
}
