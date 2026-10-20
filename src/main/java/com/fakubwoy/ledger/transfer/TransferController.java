package com.fakubwoy.ledger.transfer;

import com.fakubwoy.ledger.transfer.TransferService.Posted;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

	private final TransferService service;

	public TransferController(TransferService service) {
		this.service = service;
	}

	// Amounts are long minor units (paise for INR), never decimals.
	public record TransferRequest(@NotNull UUID fromAccountId, @NotNull UUID toAccountId, @Positive long amount) {
	}

	public record DepositRequest(@Positive long amount) {
	}

	@PostMapping("/transfers")
	@ResponseStatus(HttpStatus.CREATED)
	public Posted transfer(@Valid @RequestBody TransferRequest request) {
		return service.transfer(request.fromAccountId(), request.toAccountId(), request.amount());
	}

	@PostMapping("/accounts/{id}/deposits")
	@ResponseStatus(HttpStatus.CREATED)
	public Posted deposit(@PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
		return service.deposit(id, request.amount());
	}
}
