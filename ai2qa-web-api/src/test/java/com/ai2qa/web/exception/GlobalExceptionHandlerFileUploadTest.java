package com.ai2qa.web.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler File Upload Tests")
class GlobalExceptionHandlerFileUploadTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should map MaxUploadSizeExceededException to 413")
    void mapsMaxUploadSizeToPayloadTooLarge() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(2 * 1024 * 1024);

        ProblemDetail result = handler.handleMaxUploadSize(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(result.getTitle()).isEqualTo("Payload Too Large");
        assertThat(result.getDetail()).contains("2MB");
        assertThat(result.getType().toString()).contains("payload-too-large");
    }

    @Test
    @DisplayName("Should map IOException to 400")
    void mapsIOExceptionToBadRequest() {
        IOException ex = new IOException("Failed to parse PDF file");

        ProblemDetail result = handler.handleIOException(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("File Processing Error");
        assertThat(result.getDetail()).contains("Failed to parse PDF file");
        assertThat(result.getType().toString()).contains("file-processing-error");
    }

    @Test
    @DisplayName("Should map NoResourceFoundException to 404 for bot probes like /actuator/env")
    void mapsNoResourceFoundToNotFound() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "actuator/env");

        ProblemDetail result = handler.handleNoResourceFound(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getTitle()).isEqualTo("Not Found");
        assertThat(result.getDetail()).isEqualTo("Resource not found");
        assertThat(result.getType().toString()).contains("not-found");
    }

    @Test
    @DisplayName("Should handle NoResourceFoundException for various probe paths")
    void handlesVariousBotProbePaths() {
        String[] probePaths = {"actuator/env", ".env", "wp-admin", "actuator/configprops"};

        for (String path : probePaths) {
            NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, path);

            ProblemDetail result = handler.handleNoResourceFound(ex);

            assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }
}
