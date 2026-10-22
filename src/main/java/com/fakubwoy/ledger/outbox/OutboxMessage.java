package com.fakubwoy.ledger.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID aggregateId;

	private String payload;

	private Instant createdAt;

	private Instant publishedAt;

	private String traceparent;

	protected OutboxMessage() {
		// for JPA
	}

	public OutboxMessage(UUID aggregateId, String payload) {
		this.aggregateId = aggregateId;
		this.payload = payload;
		this.createdAt = Instant.now();
		// Empty (null) when no tracing agent is attached, e.g. in unit tests.
		var carrier = new HashMap<String, String>();
		W3CTraceContextPropagator.getInstance().inject(Context.current(), carrier, Map::put);
		this.traceparent = carrier.get("traceparent");
	}

	/** The context of the request that wrote this row, or the root context if it was not traced. */
	public Context originContext() {
		if (traceparent == null) {
			return Context.root();
		}
		return W3CTraceContextPropagator.getInstance().extract(Context.root(), Map.of("traceparent", traceparent),
				new TextMapGetter<Map<String, String>>() {
					@Override
					public Iterable<String> keys(Map<String, String> c) {
						return c.keySet();
					}

					@Override
					public String get(Map<String, String> c, String key) {
						return c == null ? null : c.get(key);
					}
				});
	}

	public UUID getAggregateId() {
		return aggregateId;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public void markPublished() {
		this.publishedAt = Instant.now();
	}
}
