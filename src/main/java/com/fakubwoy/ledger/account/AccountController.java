package com.fakubwoy.ledger.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fakubwoy.ledger.transfer.EntryRepository;
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
	private final EntryRepository entries;

	public AccountController(AccountRepository accounts, EntryRepository entries) {
		this.accounts = accounts;
		this.entries = entries;
	}

	@PostMapping
	public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
		var account = accounts.save(new Account(request.owner(), request.currency()));
		return ResponseEntity.created(URI.create("/accounts/" + account.getId()))
				.body(AccountResponse.from(account, 0));
	}

	@GetMapping("/{id}")
	public AccountResponse get(@PathVariable UUID id) {
		return accounts.findById(id)
				.map(a -> AccountResponse.from(a, entries.balanceOf(id)))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account " + id + " not found"));
	}

	public record CreateAccountRequest(@NotBlank @Size(max = 255) String owner, @NotBlank String currency) {

		@AssertTrue(message = "must be an ISO 4217 currency code, e.g. INR")
		public boolean isCurrencyValid() {
			return currency == null || Currency.getAvailableCurrencies().stream()
					.anyMatch(c -> c.getCurrencyCode().equals(currency));
		}
	}

	// balance is in minor units and derived from the sum of entries.
	public record AccountResponse(UUID id, String owner, String currency, long balance, Instant createdAt) {

		static AccountResponse from(Account a, long balance) {
			return new AccountResponse(a.getId(), a.getOwner(), a.getCurrency(), balance, a.getCreatedAt());
		}
	}
}
