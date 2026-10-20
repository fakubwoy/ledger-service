package com.fakubwoy.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

// Boot disables metric exporters in tests unless asked, so the Prometheus endpoint needs this.
@AutoConfigureObservability
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpsEndpointsTest {

	@Autowired
	TestRestTemplate rest;

	@Test
	void generatesRequestIdWhenMissing() {
		var response = rest.getForEntity("/actuator/health", String.class);

		assertThat(response.getHeaders().getFirst("X-Request-Id")).matches("[0-9a-f-]{36}");
	}

	@Test
	void echoesSafeRequestIdAndReplacesUnsafeOne() {
		assertThat(requestIdFor("trace-123")).isEqualTo("trace-123");
		assertThat(requestIdFor("bad id with spaces")).isNotEqualTo("bad id with spaces").matches("[0-9a-f-]{36}");
	}

	@Test
	void servesOpenApiDocsAndSwaggerUi() {
		var docs = rest.getForEntity("/v3/api-docs", String.class);

		assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(docs.getBody()).contains("/transfers", "/accounts/{id}/statement", "Idempotency-Key");
		assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void exposesPrometheusMetricsIncludingBusinessCounter() {
		var account = rest.postForEntity("/accounts", Map.of("owner", "test", "currency", "INR"), Map.class)
				.getBody().get("id");
		rest.postForEntity("/accounts/" + account + "/deposits", Map.of("amount", 100), String.class);

		var metrics = rest.getForEntity("/actuator/prometheus", String.class);

		assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(metrics.getBody()).contains("ledger_transactions_posted_total{type=\"DEPOSIT\"}",
				"http_server_requests_seconds_count");
	}

	private String requestIdFor(String id) {
		var headers = new HttpHeaders();
		headers.set("X-Request-Id", id);
		return rest.exchange("/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class)
				.getHeaders().getFirst("X-Request-Id");
	}
}
