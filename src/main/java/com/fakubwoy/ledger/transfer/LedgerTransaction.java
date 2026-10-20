package com.fakubwoy.ledger.transfer;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// Named LedgerTransaction to avoid confusion with database transactions (@Transactional).
@Entity
@Table(name = "transactions")
public class LedgerTransaction {

	public enum Type { TRANSFER, DEPOSIT }

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	private Type type;

	private Instant createdAt;

	protected LedgerTransaction() {
		// for JPA
	}

	public LedgerTransaction(Type type) {
		this.type = type;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Type getType() {
		return type;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
