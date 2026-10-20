package com.fakubwoy.ledger.transfer;

import com.fakubwoy.ledger.account.Account;
import com.fakubwoy.ledger.account.AccountRepository;
import com.fakubwoy.ledger.outbox.OutboxMessage;
import com.fakubwoy.ledger.outbox.OutboxRepository;
import com.fakubwoy.ledger.transfer.LedgerTransaction.Type;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

	private final AccountRepository accounts;
	private final LedgerTransactionRepository transactions;
	private final EntryRepository entries;
	private final OutboxRepository outbox;
	private final ObjectMapper json;

	public TransferService(AccountRepository accounts, LedgerTransactionRepository transactions, EntryRepository entries,
			OutboxRepository outbox, ObjectMapper json) {
		this.accounts = accounts;
		this.transactions = transactions;
		this.entries = entries;
		this.outbox = outbox;
		this.json = json;
	}

	public record Posted(UUID transactionId, Type type, UUID fromAccountId, UUID toAccountId, long amount,
			String currency, Instant createdAt) {
	}

	@Transactional
	public Posted transfer(UUID fromId, UUID toId, long amount) {
		if (fromId.equals(toId)) {
			throw new LedgerException(HttpStatus.BAD_REQUEST, "same-account", "Cannot transfer to the same account");
		}
		// Holding both row locks until commit makes the balance check and the entry writes one atomic step.
		var locked = accounts.lockAllById(List.of(fromId, toId));
		var from = pick(locked, fromId);
		var to = pick(locked, toId);
		if (!from.getCurrency().equals(to.getCurrency())) {
			throw new LedgerException(HttpStatus.UNPROCESSABLE_ENTITY, "currency-mismatch",
					"Cannot transfer %s to a %s account".formatted(from.getCurrency(), to.getCurrency()));
		}
		// READ COMMITTED: this statement sees entries committed by whoever held the lock before us.
		long balance = entries.balanceOf(fromId);
		if (balance < amount) {
			throw new LedgerException(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-funds",
					"Account %s has balance %d, transfer needs %d".formatted(fromId, balance, amount));
		}
		return post(Type.TRANSFER, fromId, toId, amount, from.getCurrency());
	}

	// Money enters the ledger from a per-currency external account, which is allowed to go negative.
	@Transactional
	public Posted deposit(UUID accountId, long amount) {
		var to = find(accountId);
		var externalId = externalAccountId(to.getCurrency());
		if (externalId.equals(accountId)) {
			throw new LedgerException(HttpStatus.BAD_REQUEST, "external-account", "Cannot deposit into an external account");
		}
		accounts.insertExternalIfAbsent(externalId, to.getCurrency());
		return post(Type.DEPOSIT, externalId, accountId, amount, to.getCurrency());
	}

	// Deterministic id, so concurrent first deposits in a currency agree on the same external account.
	static UUID externalAccountId(String currency) {
		return UUID.nameUUIDFromBytes(("external:" + currency).getBytes(StandardCharsets.UTF_8));
	}

	private Posted post(Type type, UUID fromId, UUID toId, long amount, String currency) {
		var tx = transactions.save(new LedgerTransaction(type));
		entries.saveAll(List.of(new Entry(tx.getId(), fromId, -amount), new Entry(tx.getId(), toId, amount)));
		// Same DB transaction as the entries: the event exists if and only if the money moved.
		var event = new TransferCompleted(tx.getId(), type, fromId, toId, amount, currency, tx.getCreatedAt());
		outbox.save(new OutboxMessage(tx.getId(), write(event)));
		return new Posted(tx.getId(), type, fromId, toId, amount, currency, tx.getCreatedAt());
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private Account find(UUID id) {
		return accounts.findById(id).orElseThrow(() -> notFound(id));
	}

	private static Account pick(List<Account> accounts, UUID id) {
		return accounts.stream().filter(a -> a.getId().equals(id)).findFirst().orElseThrow(() -> notFound(id));
	}

	private static LedgerException notFound(UUID id) {
		return new LedgerException(HttpStatus.NOT_FOUND, "account-not-found", "Account " + id + " not found");
	}
}
