package com.waterquality.api;

import com.waterquality.util.Database;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.sql.Connection;
import java.util.Map;

@Path("health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @GET
    public Response health() {
        return Response.ok(Map.of("status", "UP")).build();
    }

    @GET
    @Path("db")
    public Response healthDb() {
        try (Connection c = Database.getConnection()) {
            return Response.ok(Map.of("status", "UP", "database", "connected")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("status", "DOWN", "message", e.getMessage()))
                    .build();
        }
    }
}
