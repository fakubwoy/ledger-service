package com.fakubwoy.ledger.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/accounts")
public class AccountController {

	private final AccountRepository accounts;

	public AccountController(AccountRepository accounts) {
		this.accounts = accounts;
	}

	@PostMapping
	public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
		var account = accounts.save(new Account(request.owner(), request.currency()));
		return ResponseEntity.created(URI.create("/accounts/" + account.getId()))
				.body(AccountResponse.from(account));
	}

	@GetMapping("/{id}")
	public AccountResponse get(@PathVariable UUID id) {
		return accounts.findById(id)
				.map(AccountResponse::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account " + id + " not found"));
	}

	public record CreateAccountRequest(@NotBlank @Size(max = 255) String owner, @NotBlank String currency) {

		@AssertTrue(message = "must be an ISO 4217 currency code, e.g. INR")
		public boolean isCurrencyValid() {
			return currency == null || Currency.getAvailableCurrencies().stream()
					.anyMatch(c -> c.getCurrencyCode().equals(currency));
		}
	}

	public record AccountResponse(UUID id, String owner, String currency, Instant createdAt) {

		static AccountResponse from(Account a) {
			return new AccountResponse(a.getId(), a.getOwner(), a.getCurrency(), a.getCreatedAt());
		}
	}
}
