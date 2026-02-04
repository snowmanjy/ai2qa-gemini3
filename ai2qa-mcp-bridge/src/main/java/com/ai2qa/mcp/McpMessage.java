package com.ai2qa.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * JSON-RPC 2.0 message types for MCP communication.
 */
public sealed interface McpMessage {

    /**
     * JSON-RPC request message.
     *
     * @param jsonrpc Protocol version (always "2.0")
     * @param id      Request ID for correlation
     * @param method  Method name to invoke
     * @param params  Method parameters
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Request(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("id") long id,
            @JsonProperty("method") String method,
            @JsonProperty("params") Map<String, Object> params
    ) implements McpMessage {

        public Request(long id, String method, Map<String, Object> params) {
            this("2.0", id, method, params);
        }

        public Request(long id, String method) {
            this("2.0", id, method, null);
        }
    }

    /**
     * JSON-RPC response message.
     *
     * @param jsonrpc Protocol version (always "2.0")
     * @param id      Request ID for correlation
     * @param result  Successful result (mutually exclusive with error)
     * @param error   Error details (mutually exclusive with result)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("id") long id,
            @JsonProperty("result") Object result,
            @JsonProperty("error") ErrorDetails error
    ) implements McpMessage {

        public boolean isSuccess() {
            return error == null;
        }

        public boolean isError() {
            return error != null;
        }
    }

    /**
     * JSON-RPC error details.
     *
     * @param code    Error code
     * @param message Error message
     * @param data    Additional error data
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorDetails(
            @JsonProperty("code") int code,
            @JsonProperty("message") String message,
            @JsonProperty("data") Object data
    ) {}

    /**
     * JSON-RPC notification (no response expected).
     *
     * @param jsonrpc Protocol version (always "2.0")
     * @param method  Method name
     * @param params  Method parameters
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Notification(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("method") String method,
            @JsonProperty("params") Map<String, Object> params
    ) implements McpMessage {

        public Notification(String method, Map<String, Object> params) {
            this("2.0", method, params);
        }
    }

    // Standard JSON-RPC error codes
    int PARSE_ERROR = -32700;
    int INVALID_REQUEST = -32600;
    int METHOD_NOT_FOUND = -32601;
    int INVALID_PARAMS = -32602;
    int INTERNAL_ERROR = -32603;
}
