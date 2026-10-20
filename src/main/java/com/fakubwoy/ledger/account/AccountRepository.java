package com.fakubwoy.ledger.account;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	// SELECT ... FOR UPDATE in id order: every transaction locks rows in the same order, so no deadlocks.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.id in :ids order by a.id")
	List<Account> lockAllById(Collection<UUID> ids);

	@Modifying
	@Query(value = """
			insert into accounts (id, owner, currency, created_at)
			values (:id, 'external', :currency, now())
			on conflict (id) do nothing""", nativeQuery = true)
	void insertExternalIfAbsent(UUID id, String currency);
}
