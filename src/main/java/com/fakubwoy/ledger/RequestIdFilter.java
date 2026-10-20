package com.fakubwoy.ledger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Tags every log line of a request with requestId and echoes it back as X-Request-Id.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Request-Id";
	static final String MDC_KEY = "requestId";

	// Client-supplied ids end up in logs, so only accept a safe shape (no newlines or log injection).
	private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		var incoming = request.getHeader(HEADER);
		var requestId = incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		try {
			chain.doFilter(request, response);
		} finally {
			// Tomcat reuses threads; without this the next request would inherit our id.
			MDC.remove(MDC_KEY);
		}
	}
}
