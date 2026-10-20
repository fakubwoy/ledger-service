package com.fakubwoy.ledger.transfer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

	@Query("select coalesce(sum(e.amount), 0) from Entry e where e.accountId = :accountId")
	long balanceOf(UUID accountId);

	List<Entry> findByTransactionId(UUID transactionId);
}
