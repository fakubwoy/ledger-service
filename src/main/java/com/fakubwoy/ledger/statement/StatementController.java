package com.fakubwoy.ledger.statement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Eventually consistent: lines appear shortly after the transfer, once the event is consumed.
@RestController
public class StatementController {

	private final JdbcClient jdbc;

	public StatementController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record StatementLine(UUID transactionId, String type, UUID counterpartyAccountId, long amount,
			String currency, Instant occurredAt) {
	}

	// ponytail: unbounded list; milestone 7 adds pagination to history.
	@GetMapping("/accounts/{id}/statement")
	public List<StatementLine> statement(@PathVariable UUID id) {
		return jdbc.sql("""
				select transaction_id, type, counterparty_account_id, amount, currency, occurred_at
				from statement_lines where account_id = ? order by occurred_at desc, transaction_id""")
				.param(id)
				.query(StatementLine.class)
				.list();
	}
}
