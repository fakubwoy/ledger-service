package com.fakubwoy.ledger.transfer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
}
