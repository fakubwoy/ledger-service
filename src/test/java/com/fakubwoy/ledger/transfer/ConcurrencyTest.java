package com.fakubwoy.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fakubwoy.ledger.TestcontainersConfiguration;
import com.fakubwoy.ledger.account.AccountController.AccountResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyTest {

	static final int THREADS = 100;

	@Autowired
	TestRestTemplate rest;

	@Test
	void hundredParallelTransfersNeverOverdraw() throws Exception {
		var from = createAccount();
		var to = createAccount();
		deposit(from, 5_000);

		// 100 transfers of 100 against a balance of 5000: exactly 50 can succeed.
		var statuses = runInParallel(i -> transfer(from, to, 100));

		assertThat(statuses.stream().filter(s -> s.value() == 201)).hasSize(50);
		assertThat(statuses.stream().filter(s -> s.value() == 422)).hasSize(50);
		assertThat(balance(from)).isZero();
		assertThat(balance(to)).isEqualTo(5_000);
	}

	@Test
	void opposingTransfersDoNotDeadlock() throws Exception {
		var a = createAccount();
		var b = createAccount();
		deposit(a, 10_000);
		deposit(b, 10_000);

		// Half go A->B, half B->A. Locking "from" then "to" in separate queries deadlocks here
		// (verified); locking both rows in one query in id order does not.
		var statuses = runInParallel(i -> i % 2 == 0 ? transfer(a, b, 100) : transfer(b, a, 100));

		assertThat(statuses).allMatch(s -> s.value() == 201);
		assertThat(balance(a) + balance(b)).isEqualTo(20_000);
	}

	interface Call {
		HttpStatusCode run(int i);
	}

	private List<HttpStatusCode> runInParallel(Call call) throws Exception {
		var start = new CountDownLatch(1);
		var tasks = new ArrayList<Callable<HttpStatusCode>>();
		for (int i = 0; i < THREADS; i++) {
			int n = i;
			tasks.add(() -> {
				start.await();
				return call.run(n);
			});
		}
		var pool = Executors.newFixedThreadPool(THREADS);
		try {
			var futures = tasks.stream().map(pool::submit).toList();
			start.countDown();
			var statuses = new ArrayList<HttpStatusCode>();
			for (var f : futures) {
				statuses.add(f.get());
			}
			return statuses;
		} finally {
			pool.shutdown();
		}
	}

	private HttpStatusCode transfer(UUID from, UUID to, long amount) {
		return rest.postForEntity("/transfers", Map.of("fromAccountId", from, "toAccountId", to, "amount", amount),
				String.class).getStatusCode();
	}

	private UUID createAccount() {
		return rest.postForEntity("/accounts", Map.of("owner", "test", "currency", "INR"), AccountResponse.class)
				.getBody().id();
	}

	private void deposit(UUID account, long amount) {
		rest.postForEntity("/accounts/" + account + "/deposits", Map.of("amount", amount), String.class);
	}

	private long balance(UUID account) {
		return rest.getForEntity("/accounts/" + account, AccountResponse.class).getBody().balance();
	}
}
