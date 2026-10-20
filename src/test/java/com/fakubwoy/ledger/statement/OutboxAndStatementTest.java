package com.fakubwoy.ledger.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fakubwoy.ledger.TestcontainersConfiguration;
import com.fakubwoy.ledger.account.AccountController.AccountResponse;
import com.fakubwoy.ledger.outbox.OutboxRepository;
import com.fakubwoy.ledger.statement.StatementController.StatementLine;
import com.fakubwoy.ledger.transfer.LedgerTransaction.Type;
import com.fakubwoy.ledger.transfer.TransferCompleted;
import com.fakubwoy.ledger.transfer.TransferService.Posted;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboxAndStatementTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	OutboxRepository outbox;

	@Autowired
	StatementConsumer consumer;

	@Autowired
	ObjectMapper json;

	@Test
	void transferIsPublishedViaOutboxAndLandsOnBothStatements() {
		var from = createAccount();
		var to = createAccount();
		deposit(from, 10_000);

		var transfer = rest.postForEntity("/transfers",
				Map.of("fromAccountId", from, "toAccountId", to, "amount", 2_500), Posted.class).getBody();

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(outbox.findByAggregateId(transfer.transactionId()))
					.singleElement().satisfies(m -> assertThat(m.getPublishedAt()).isNotNull());
			assertThat(statement(from)).extracting(StatementLine::amount).containsExactly(-2_500L, 10_000L);
			assertThat(statement(to)).singleElement().satisfies(line -> {
				assertThat(line.amount()).isEqualTo(2_500);
				assertThat(line.counterpartyAccountId()).isEqualTo(from);
				assertThat(line.type()).isEqualTo("TRANSFER");
			});
		});
	}

	@Test
	void failedTransferWritesNoOutboxEvent() {
		var from = createAccount();
		var to = createAccount();

		var response = rest.postForEntity("/transfers",
				Map.of("fromAccountId", from, "toAccountId", to, "amount", 100), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(outbox.findAll()).noneMatch(m -> m.getPayload().contains(from.toString()));
	}

	@Test
	void redeliveredEventIsIgnored() throws Exception {
		var a = UUID.randomUUID();
		var b = UUID.randomUUID();
		var event = new TransferCompleted(UUID.randomUUID(), Type.TRANSFER, a, b, 700, "INR", Instant.now());
		var payload = json.writeValueAsString(event);

		consumer.on(payload);
		consumer.on(payload);

		assertThat(statement(a)).singleElement().extracting(StatementLine::amount).isEqualTo(-700L);
		assertThat(statement(b)).singleElement().extracting(StatementLine::amount).isEqualTo(700L);
	}

	private List<StatementLine> statement(UUID account) {
		return rest.exchange("/accounts/" + account + "/statement", HttpMethod.GET, null,
				new ParameterizedTypeReference<List<StatementLine>>() {
				}).getBody();
	}

	private UUID createAccount() {
		return rest.postForEntity("/accounts", Map.of("owner", "test", "currency", "INR"), AccountResponse.class)
				.getBody().id();
	}

	private void deposit(UUID account, long amount) {
		rest.postForEntity("/accounts/" + account + "/deposits", Map.of("amount", amount), String.class);
	}
}
