package com.fakubwoy.ledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fakubwoy.ledger.TestcontainersConfiguration;
import com.fakubwoy.ledger.account.AccountController.AccountResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountControllerTest {

	@Autowired
	TestRestTemplate rest;

	@Test
	void createsAndFetchesAccount() {
		var created = rest.postForEntity("/accounts", Map.of("owner", "Farhaan", "currency", "INR"), AccountResponse.class);

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		var body = created.getBody();
		assertThat(body.id()).isNotNull();
		assertThat(body.createdAt()).isNotNull();
		assertThat(created.getHeaders().getLocation()).hasPath("/accounts/" + body.id());

		var fetched = rest.getForEntity("/accounts/" + body.id(), AccountResponse.class);
		assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(fetched.getBody().owner()).isEqualTo("Farhaan");
		assertThat(fetched.getBody().currency()).isEqualTo("INR");
	}

	@Test
	void unknownAccountIs404ProblemDetail() {
		var response = rest.getForEntity("/accounts/" + UUID.randomUUID(), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void rejectsInvalidCurrency() {
		var response = rest.postForEntity("/accounts", Map.of("owner", "Farhaan", "currency", "XYZ"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void rejectsBlankOwner() {
		var response = rest.postForEntity("/accounts", Map.of("owner", " ", "currency", "INR"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}
}
