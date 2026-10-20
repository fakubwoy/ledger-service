package com.fakubwoy.ledger.account;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	@Modifying
	@Query(value = """
			insert into accounts (id, owner, currency, created_at)
			values (:id, 'external', :currency, now())
			on conflict (id) do nothing""", nativeQuery = true)
	void insertExternalIfAbsent(UUID id, String currency);
}
