package com.fakubwoy.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fakubwoy.ledger.TestcontainersConfiguration;
import com.fakubwoy.ledger.account.AccountController.AccountResponse;
import com.fakubwoy.ledger.transfer.TransferService.Posted;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferControllerTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	EntryRepository entries;

	@Test
	void depositCreditsAccountFromExternalAccount() {
		var account = createAccount("INR");

		var response = rest.postForEntity("/accounts/" + account + "/deposits", Map.of("amount", 10_000), Posted.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().type()).isEqualTo(LedgerTransaction.Type.DEPOSIT);
		assertThat(balance(account)).isEqualTo(10_000);
		assertThat(response.getBody().fromAccountId()).isEqualTo(TransferService.externalAccountId("INR"));
	}

	@Test
	void transferWritesBalancedEntriesAndMovesMoney() {
		var from = createAccount("INR");
		var to = createAccount("INR");
		deposit(from, 10_000);

		var response = rest.postForEntity("/transfers",
				Map.of("fromAccountId", from, "toAccountId", to, "amount", 3_000), Posted.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(balance(from)).isEqualTo(7_000);
		assertThat(balance(to)).isEqualTo(3_000);
		var txEntries = entries.findByTransactionId(response.getBody().transactionId());
		assertThat(txEntries).extracting(Entry::getAmount).containsExactlyInAnyOrder(-3_000L, 3_000L);
	}

	@Test
	void rejectsInsufficientFunds() {
		var from = createAccount("INR");
		var to = createAccount("INR");
		deposit(from, 1_000);

		var problem = transferExpectingProblem(from, to, 1_001);

		assertThat(problem.getStatus()).isEqualTo(422);
		assertThat(problem.getType()).hasPath("/problems/insufficient-funds");
		assertThat(balance(from)).isEqualTo(1_000);
		assertThat(balance(to)).isZero();
	}

	@Test
	void rejectsCurrencyMismatch() {
		var from = createAccount("INR");
		var to = createAccount("USD");
		deposit(from, 1_000);

		var problem = transferExpectingProblem(from, to, 500);

		assertThat(problem.getStatus()).isEqualTo(422);
		assertThat(problem.getType()).hasPath("/problems/currency-mismatch");
	}

	@Test
	void rejectsUnknownAccount() {
		var problem = transferExpectingProblem(createAccount("INR"), UUID.randomUUID(), 100);

		assertThat(problem.getStatus()).isEqualTo(404);
		assertThat(problem.getType()).hasPath("/problems/account-not-found");
	}

	@Test
	void rejectsTransferToSameAccount() {
		var account = createAccount("INR");

		assertThat(transferExpectingProblem(account, account, 100).getStatus()).isEqualTo(400);
	}

	@Test
	void rejectsNonPositiveAmounts() {
		var from = createAccount("INR");
		var to = createAccount("INR");

		assertThat(transferExpectingProblem(from, to, 0).getStatus()).isEqualTo(400);
		var deposit = rest.postForEntity("/accounts/" + from + "/deposits", Map.of("amount", -5), ProblemDetail.class);
		assertThat(deposit.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private ProblemDetail transferExpectingProblem(UUID from, UUID to, long amount) {
		return rest.postForEntity("/transfers", Map.of("fromAccountId", from, "toAccountId", to, "amount", amount),
				ProblemDetail.class).getBody();
	}

	private UUID createAccount(String currency) {
		return rest.postForEntity("/accounts", Map.of("owner", "test", "currency", currency), AccountResponse.class)
				.getBody().id();
	}

	private void deposit(UUID account, long amount) {
		var response = rest.postForEntity("/accounts/" + account + "/deposits", Map.of("amount", amount), Posted.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	private long balance(UUID account) {
		return rest.getForEntity("/accounts/" + account, AccountResponse.class).getBody().balance();
	}
}
