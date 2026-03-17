package com.waterquality.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.Map;

/**
 * 任何未捕获异常都会变成 500，并在响应 body 里返回错误信息和堆栈，便于在浏览器/Network 里直接看到原因。
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable ex) {
        ex.printStackTrace();

        String stackTrace = null;
        try {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            stackTrace = sw.toString();
        } catch (Exception ignored) {}

        String message = ex.getMessage();
        if (message == null) message = ex.getClass().getName();

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "error", "Internal Server Error",
                        "message", message,
                        "type", ex.getClass().getName(),
                        "stackTrace", stackTrace != null ? stackTrace : ""
                ))
                .build();
    }
}
