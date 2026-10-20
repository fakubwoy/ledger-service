package com.fakubwoy.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fakubwoy.ledger.account.Account;
import com.fakubwoy.ledger.account.AccountRepository;
import com.fakubwoy.ledger.outbox.OutboxMessage;
import com.fakubwoy.ledger.outbox.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// Domain rules of a transfer, with the database mocked out: runs in milliseconds.
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

	@Mock
	AccountRepository accounts;

	@Mock
	LedgerTransactionRepository transactions;

	@Mock
	EntryRepository entries;

	@Mock
	OutboxRepository outbox;

	SimpleMeterRegistry meters = new SimpleMeterRegistry();

	TransferService service;

	@BeforeEach
	void setUp() {
		service = new TransferService(accounts, transactions, entries, outbox, new ObjectMapper().findAndRegisterModules(),
				meters);
	}

	@Test
	void sameAccountIsRejectedBeforeTouchingTheDatabase() {
		var id = UUID.randomUUID();

		assertProblem(() -> service.transfer(id, id, 100), 400, "same-account");
		verifyNoInteractions(accounts, entries, outbox);
	}

	@Test
	void unknownAccountIs404() {
		var from = account("INR");
		var missing = UUID.randomUUID();
		when(accounts.lockAllById(List.of(from.getId(), missing))).thenReturn(List.of(from));

		assertProblem(() -> service.transfer(from.getId(), missing, 100), 404, "account-not-found");
	}

	@Test
	void currencyMismatchWritesNothing() {
		var from = account("INR");
		var to = account("USD");
		locks(from, to);

		assertProblem(() -> service.transfer(from.getId(), to.getId(), 100), 422, "currency-mismatch");
		verify(entries, never()).saveAll(any());
		verifyNoInteractions(outbox);
	}

	@Test
	void insufficientFundsWritesNothing() {
		var from = account("INR");
		var to = account("INR");
		locks(from, to);
		when(entries.balanceOf(from.getId())).thenReturn(99L);

		assertProblem(() -> service.transfer(from.getId(), to.getId(), 100), 422, "insufficient-funds");
		verify(entries, never()).saveAll(any());
		verifyNoInteractions(outbox);
	}

	@Test
	@SuppressWarnings("unchecked")
	void transferOfExactBalanceWritesBalancedEntriesAndOutboxEvent() {
		var from = account("INR");
		var to = account("INR");
		locks(from, to);
		when(entries.balanceOf(from.getId())).thenReturn(100L);
		var txId = savesTransactionWithId();

		var posted = service.transfer(from.getId(), to.getId(), 100);

		ArgumentCaptor<List<Entry>> saved = ArgumentCaptor.forClass(List.class);
		verify(entries).saveAll(saved.capture());
		assertThat(saved.getValue()).extracting(Entry::getAmount).containsExactly(-100L, 100L);
		assertThat(saved.getValue().stream().mapToLong(Entry::getAmount).sum()).isZero();
		var event = ArgumentCaptor.forClass(OutboxMessage.class);
		verify(outbox).save(event.capture());
		assertThat(event.getValue().getPayload()).contains(txId.toString(), "\"amount\":100");
		assertThat(posted.transactionId()).isEqualTo(txId);
		assertThat(meters.counter("ledger.transactions.posted", "type", "TRANSFER").count()).isEqualTo(1);
	}

	@Test
	void depositDebitsTheExternalAccountForItsCurrency() {
		var to = account("INR");
		when(accounts.findById(to.getId())).thenReturn(Optional.of(to));
		savesTransactionWithId();

		var posted = service.deposit(to.getId(), 500);

		var external = TransferService.externalAccountId("INR");
		verify(accounts).insertExternalIfAbsent(external, "INR");
		assertThat(posted.fromAccountId()).isEqualTo(external);
		assertThat(posted.type()).isEqualTo(LedgerTransaction.Type.DEPOSIT);
	}

	private static Account account(String currency) {
		var account = new Account("test", currency);
		ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
		return account;
	}

	private void locks(Account from, Account to) {
		when(accounts.lockAllById(List.of(from.getId(), to.getId()))).thenReturn(List.of(from, to));
	}

	private UUID savesTransactionWithId() {
		var id = UUID.randomUUID();
		when(transactions.save(any())).thenAnswer(inv -> {
			LedgerTransaction tx = inv.getArgument(0);
			ReflectionTestUtils.setField(tx, "id", id);
			return tx;
		});
		return id;
	}

	private static void assertProblem(Runnable call, int status, String type) {
		var e = catchThrowableOfType(LedgerException.class, call::run);
		assertThat(e).as("expected LedgerException").isNotNull();
		assertThat(e.getStatusCode().value()).isEqualTo(status);
		assertThat(e.getBody().getType()).hasPath("/problems/" + type);
	}
}
