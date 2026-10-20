package com.fakubwoy.ledger.transfer;

import com.fakubwoy.ledger.idempotency.IdempotencyService;
import com.fakubwoy.ledger.transfer.TransferService.Posted;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

	private final TransferService service;
	private final IdempotencyService idempotency;

	public TransferController(TransferService service, IdempotencyService idempotency) {
		this.service = service;
		this.idempotency = idempotency;
	}

	static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	// Amounts are long minor units (paise for INR), never decimals.
	public record TransferRequest(@NotNull UUID fromAccountId, @NotNull UUID toAccountId, @Positive long amount) {
	}

	public record DepositRequest(@Positive long amount) {
	}

	@PostMapping("/transfers")
	@ResponseStatus(HttpStatus.CREATED)
	public Posted transfer(@RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = 255) String key,
			@Valid @RequestBody TransferRequest request) {
		return idempotency.execute(key, "POST /transfers", request, Posted.class,
				() -> service.transfer(request.fromAccountId(), request.toAccountId(), request.amount()));
	}

	@PostMapping("/accounts/{id}/deposits")
	@ResponseStatus(HttpStatus.CREATED)
	public Posted deposit(@RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = 255) String key,
			@PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
		return idempotency.execute(key, "POST /accounts/" + id + "/deposits", request, Posted.class,
				() -> service.deposit(id, request.amount()));
	}
}
