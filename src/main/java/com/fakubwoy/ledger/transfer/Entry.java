package com.fakubwoy.ledger.transfer;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "entries")
public class Entry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID transactionId;

	private UUID accountId;

	private long amount;

	protected Entry() {
		// for JPA
	}

	public Entry(UUID transactionId, UUID accountId, long amount) {
		this.transactionId = transactionId;
		this.accountId = accountId;
		this.amount = amount;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTransactionId() {
		return transactionId;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public long getAmount() {
		return amount;
	}
}
