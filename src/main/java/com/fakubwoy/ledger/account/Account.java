package com.fakubwoy.ledger.account;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String owner;

	private String currency;

	private Instant createdAt;

	protected Account() {
		// for JPA
	}

	public Account(String owner, String currency) {
		this.owner = owner;
		this.currency = currency;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getOwner() {
		return owner;
	}

	public String getCurrency() {
		return currency;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
