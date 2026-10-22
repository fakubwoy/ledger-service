package com.fakubwoy.ledger.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

	// SKIP LOCKED lets several app instances poll at once without sending the same rows.
	@Query(value = """
			select * from outbox where published_at is null
			order by id limit :limit
			for update skip locked""", nativeQuery = true)
	List<OutboxMessage> lockUnpublished(int limit);

	long countByPublishedAtIsNull();

	List<OutboxMessage> findByAggregateId(UUID aggregateId);
}
