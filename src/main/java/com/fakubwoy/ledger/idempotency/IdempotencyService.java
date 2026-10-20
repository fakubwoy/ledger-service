package com.fakubwoy.ledger.idempotency;

import com.fakubwoy.ledger.transfer.LedgerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

	private final IdempotencyKeyRepository keys;
	private final ObjectMapper json;

	public IdempotencyService(IdempotencyKeyRepository keys, ObjectMapper json) {
		this.keys = keys;
		this.json = json;
	}

	/**
	 * Runs action at most once per key. The key, the action's writes and the stored response
	 * commit in one transaction, so if the action fails the key is released and a retry runs fresh.
	 * ponytail: keys never expire; add a cleanup job (e.g. delete after 24h) before real traffic.
	 */
	@Transactional
	public <T> T execute(String key, String endpoint, Object request, Class<T> responseType, Supplier<T> action) {
		if (key == null) {
			return action.get();
		}
		var hash = sha256(endpoint + " " + write(request));
		if (keys.insertIfAbsent(key, hash) == 1) {
			T result = action.get();
			keys.findById(key).orElseThrow().setResponse(write(result));
			return result;
		}
		var existing = keys.findById(key).orElseThrow();
		if (!existing.getRequestHash().equals(hash)) {
			throw new LedgerException(HttpStatus.CONFLICT, "idempotency-key-reused",
					"Idempotency-Key was already used with a different request");
		}
		return read(existing.getResponse(), responseType);
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private <T> T read(String value, Class<T> type) {
		try {
			return json.readValue(value, type);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String sha256(String value) {
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
