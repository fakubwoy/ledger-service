package com.fakubwoy.ledger.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

	// Returns 1 if this call claimed the key, 0 if it already existed.
	// Blocks while another uncommitted transaction holds the same key.
	@Modifying
	@Query(value = """
			insert into idempotency_keys (key, request_hash, created_at)
			values (:key, :requestHash, now())
			on conflict (key) do nothing""", nativeQuery = true)
	int insertIfAbsent(String key, String requestHash);
}
