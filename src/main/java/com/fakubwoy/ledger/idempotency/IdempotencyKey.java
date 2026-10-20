package com.fakubwoy.ledger.idempotency;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

	@Id
	private String key;

	private String requestHash;

	private String response;

	private Instant createdAt;

	protected IdempotencyKey() {
		// for JPA; rows are inserted by IdempotencyKeyRepository.insertIfAbsent
	}

	public String getRequestHash() {
		return requestHash;
	}

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}
}
