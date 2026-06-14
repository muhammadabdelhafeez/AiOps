package org.kfh.aiops.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class HttpActionLoggingFilterTest {

    @Test
    void shouldLogSafeHttpActionMetadataWithoutQueryStringOrPayload(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setQueryString("password=DoNotLog&token=DoNotLog");
        request.setContent("{\"password\":\"DoNotLog\"}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Tenant-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-Country-Code", "KW");
        request.addHeader("X-Environment", "PROD");
        request.addHeader(CorrelationIdFilter.HEADER, "corr-users-create");
        var response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> response.setStatus(503);

        new HttpActionLoggingFilter().doFilter(request, response, chain);

        assertThat(output).contains("http action method=POST path=/api/v1/users status=503");
        assertThat(output).contains("countryCode=KW");
        assertThat(output).contains("environment=PROD");
        assertThat(output).contains("correlationId=corr-users-create");
        assertThat(output).doesNotContain("password=DoNotLog");
        assertThat(output).doesNotContain("token=DoNotLog");
        assertThat(output).doesNotContain("{\"password\":\"DoNotLog\"}");
    }
}

