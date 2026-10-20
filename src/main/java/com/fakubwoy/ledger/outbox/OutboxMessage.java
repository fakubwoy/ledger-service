package com.fakubwoy.ledger.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
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

	protected OutboxMessage() {
		// for JPA
	}

	public OutboxMessage(UUID aggregateId, String payload) {
		this.aggregateId = aggregateId;
		this.payload = payload;
		this.createdAt = Instant.now();
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
