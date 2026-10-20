package com.fakubwoy.ledger.statement;

import com.fakubwoy.ledger.transfer.TransferCompleted;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Builds the per-account statement read model. Safe to receive the same event twice.
@Component
public class StatementConsumer {

	private final JdbcClient jdbc;
	private final ObjectMapper json;

	public StatementConsumer(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@KafkaListener(topics = TransferCompleted.TOPIC, groupId = "statement-view")
	@Transactional
	public void on(String payload) throws JsonProcessingException {
		var event = json.readValue(payload, TransferCompleted.class);
		insert(event.fromAccountId(), event.toAccountId(), -event.amount(), event);
		insert(event.toAccountId(), event.fromAccountId(), event.amount(), event);
	}

	private void insert(UUID account, UUID counterparty, long amount, TransferCompleted event) {
		jdbc.sql("""
				insert into statement_lines
				    (account_id, transaction_id, type, counterparty_account_id, amount, currency, occurred_at)
				values (?, ?, ?, ?, ?, ?, ?)
				on conflict do nothing""")
				.params(account, event.transactionId(), event.type().name(), counterparty, amount, event.currency(),
						Timestamp.from(event.occurredAt()))
				.update();
	}
}
