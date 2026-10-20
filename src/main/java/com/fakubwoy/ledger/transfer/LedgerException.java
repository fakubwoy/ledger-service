package com.fakubwoy.ledger.transfer;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

// Rendered by Spring as RFC 7807 problem+json; `type` lets clients branch on the error without parsing text.
public class LedgerException extends ErrorResponseException {

	public LedgerException(HttpStatus status, String type, String detail) {
		super(status, problem(status, type, detail), null);
	}

	private static ProblemDetail problem(HttpStatus status, String type, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("/problems/" + type));
		return problem;
	}
}
