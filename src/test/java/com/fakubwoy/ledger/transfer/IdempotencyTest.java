package com.fakubwoy.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fakubwoy.ledger.TestcontainersConfiguration;
import com.fakubwoy.ledger.account.AccountController.AccountResponse;
import com.fakubwoy.ledger.transfer.TransferService.Posted;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyTest {

	@Autowired
	TestRestTemplate rest;

	@Test
	void replayingSameKeyReturnsOriginalResultAndMovesMoneyOnce() {
		var from = createAccount();
		var to = createAccount();
		deposit(from, 10_000, null);
		var key = UUID.randomUUID().toString();

		var first = transfer(key, from, to, 3_000, Posted.class);
		var replay = transfer(key, from, to, 3_000, Posted.class);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(replay.getBody()).isEqualTo(first.getBody());
		assertThat(balance(from)).isEqualTo(7_000);
	}

	@Test
	void sameKeyWithDifferentBodyIs409() {
		var from = createAccount();
		var to = createAccount();
		deposit(from, 10_000, null);
		var key = UUID.randomUUID().toString();
		transfer(key, from, to, 3_000, Posted.class);

		var conflict = transfer(key, from, to, 9_000, ProblemDetail.class);

		assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(conflict.getBody().getType()).hasPath("/problems/idempotency-key-reused");
		assertThat(balance(from)).isEqualTo(7_000);
	}

	@Test
	void failedRequestReleasesKeySoRetryRunsFresh() {
		var from = createAccount();
		var to = createAccount();
		var key = UUID.randomUUID().toString();

		assertThat(transfer(key, from, to, 500, ProblemDetail.class).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		deposit(from, 1_000, null);

		assertThat(transfer(key, from, to, 500, Posted.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(balance(to)).isEqualTo(500);
	}

	@Test
	void concurrentRetriesWithSameKeyDepositOnce() throws Exception {
		var account = createAccount();
		var key = UUID.randomUUID().toString();
		var calls = new ArrayList<Callable<ResponseEntity<Posted>>>();
		for (int i = 0; i < 10; i++) {
			calls.add(() -> deposit(account, 1_000, key));
		}

		var pool = Executors.newFixedThreadPool(10);
		try {
			for (var result : pool.invokeAll(calls)) {
				assertThat(result.get().getStatusCode()).isEqualTo(HttpStatus.CREATED);
			}
		} finally {
			pool.shutdown();
		}
		assertThat(balance(account)).isEqualTo(1_000);
	}

	@Test
	void rejectsOverlongKey() {
		var from = createAccount();

		var response = transfer("k".repeat(256), from, createAccount(), 100, ProblemDetail.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private <T> ResponseEntity<T> transfer(String key, UUID from, UUID to, long amount, Class<T> type) {
		var body = Map.of("fromAccountId", from, "toAccountId", to, "amount", amount);
		return rest.postForEntity("/transfers", new HttpEntity<>(body, keyHeader(key)), type);
	}

	private ResponseEntity<Posted> deposit(UUID account, long amount, String key) {
		return rest.postForEntity("/accounts/" + account + "/deposits",
				new HttpEntity<>(Map.of("amount", amount), keyHeader(key)), Posted.class);
	}

	private static HttpHeaders keyHeader(String key) {
		var headers = new HttpHeaders();
		if (key != null) {
			headers.set("Idempotency-Key", key);
		}
		return headers;
	}

	private UUID createAccount() {
		return rest.postForEntity("/accounts", Map.of("owner", "test", "currency", "INR"), AccountResponse.class)
				.getBody().id();
	}

	private long balance(UUID account) {
		return rest.getForEntity("/accounts/" + account, AccountResponse.class).getBody().balance();
	}
}
