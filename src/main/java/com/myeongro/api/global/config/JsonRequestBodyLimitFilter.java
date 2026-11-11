package com.myeongro.api.global.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

final class JsonRequestBodyLimitFilter extends OncePerRequestFilter {

	static final int MAX_BYTES = 32 * 1024;
	private static final MediaType SUFFIX_JSON = new MediaType("application", "*+json");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!HttpMethod.POST.matches(request.getMethod()) || request.getContentType() == null) {
			return true;
		}
		try {
			MediaType type = MediaType.parseMediaType(request.getContentType());
			return !MediaType.APPLICATION_JSON.includes(type) && !SUFFIX_JSON.includes(type);
		} catch (InvalidMediaTypeException exception) {
			return true;
		}
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request, HttpServletResponse response, FilterChain chain
	) throws ServletException, IOException {
		if (request.getContentLengthLong() > MAX_BYTES) {
			response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
			return;
		}
		byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
		if (body.length > MAX_BYTES) {
			response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
			return;
		}
		chain.doFilter(new BufferedJsonRequest(request, body), response);
	}

	private static final class BufferedJsonRequest extends HttpServletRequestWrapper {

		private final ServletInputStream stream;

		private BufferedJsonRequest(HttpServletRequest request, byte[] body) {
			super(request);
			ByteArrayInputStream bytes = new ByteArrayInputStream(body);
			this.stream = new ServletInputStream() {
				@Override
				public int read() {
					return bytes.read();
				}

				@Override
				public int read(byte[] target, int offset, int length) {
					return bytes.read(target, offset, length);
				}

				@Override
				public boolean isFinished() {
					return bytes.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new IllegalStateException("Buffered JSON requests use synchronous reads");
				}
			};
		}

		@Override
		public ServletInputStream getInputStream() {
			return stream;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			String encoding = getCharacterEncoding();
			return new BufferedReader(encoding == null
				? new InputStreamReader(stream, StandardCharsets.ISO_8859_1)
				: new InputStreamReader(stream, encoding));
		}
	}
}
