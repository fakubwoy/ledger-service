package com.fakubwoy.ledger.statement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Transaction history per account. Eventually consistent: lines appear once the event is consumed.
@RestController
public class StatementController {

	private final JdbcClient jdbc;

	public StatementController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record StatementLine(UUID transactionId, String type, UUID counterpartyAccountId, long amount,
			String currency, Instant occurredAt) {
	}

	public record StatementPage(List<StatementLine> items, int page, int size, boolean hasNext) {
	}

	// ponytail: offset pagination slows on deep pages and shifts under inserts; switch to a keyset cursor
	// on (occurred_at, transaction_id) if accounts get long histories or clients need infinite scroll.
	@GetMapping("/accounts/{id}/statement")
	public StatementPage statement(@PathVariable UUID id,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		// Fetch one extra row to know if another page exists, without a COUNT(*) query.
		var lines = jdbc.sql("""
				select transaction_id, type, counterparty_account_id, amount, currency, occurred_at
				from statement_lines where account_id = ?
				order by occurred_at desc, transaction_id
				limit ? offset ?""")
				.params(id, size + 1, (long) page * size)
				.query(StatementLine.class)
				.list();
		var hasNext = lines.size() > size;
		return new StatementPage(hasNext ? lines.subList(0, size) : lines, page, size, hasNext);
	}
}
