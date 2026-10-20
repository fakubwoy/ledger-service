package com.fakubwoy.ledger.transfer;

import com.fakubwoy.ledger.transfer.LedgerTransaction.Type;
import java.time.Instant;
import java.util.UUID;

// Event contract on Kafka, kept separate from the API response so either can change without breaking the other.
public record TransferCompleted(UUID transactionId, Type type, UUID fromAccountId, UUID toAccountId, long amount,
		String currency, Instant occurredAt) {

	public static final String TOPIC = "ledger.transfer-completed";
}
