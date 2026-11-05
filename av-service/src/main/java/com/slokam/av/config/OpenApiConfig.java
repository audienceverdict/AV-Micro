package com.slokam.av.config;

import com.slokam.av.exception.ApiErrors;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI movieBookingOpenApi() {
        var components = new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
        ModelConverters.getInstance().readAll(ApiErrors.class).forEach(components::addSchemas);
        return new OpenAPI().components(components).info(new Info()
                .title("AV Movie Booking API").version("1.0.0")
                .description("Movie catalog, people, theatres, versioned seating layouts, shows, bookings, payments, reviews, waiting lists and notifications. "
                        + "IDs are opaque strings. HTTP/HTTPS URLs are required for catalog media and social links. "
                        + "Sign in using email OTP at /api/v1/auth/otp/request and /verify. Registration verifies WhatsApp and email through /registration endpoints before /register. "
                        + "Paste accessToken into Authorize (without the Bearer prefix). ADMIN operations require an enabled ADMIN account. "
                        + "Pagination is zero-based (page=0, size=20). Dates use YYYY-MM-DD and timestamps use ISO-8601. "
                        + "Response codes reflect the controllers: creation and deletion return 200 unless explicitly documented as 201/204. "
                        + "Validation errors include fieldErrors. Business errors use the same ApiErrors envelope and a specific error code."));
    }

    @Bean
    OpenApiCustomizer accessAndErrorDocumentation() {
        return api -> {
            ModelConverters.getInstance().readAll(ApiErrors.class).forEach(api.getComponents()::addSchemas);
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            boolean ticketScan = method.name().equals("POST") && path.equals("/api/v1/bookings/admin/ticket-scan");
            boolean verifier = path.startsWith("/api/v1/verifier/");
            boolean get = method.name().equals("GET");
            boolean admin = path.startsWith("/api/v1/admin/") || path.contains("/admin")
                    || path.endsWith("/layout-compatibility")
                    || (!get && (path.startsWith("/api/v1/movies") || path.startsWith("/api/v1/people")
                    || path.startsWith("/api/v1/theatres") || path.startsWith("/api/v1/shows")));
            boolean authenticated = verifier || admin || path.equals("/api/v1/auth/me")
                    || path.startsWith("/api/v1/bookings") || path.startsWith("/api/v1/waiting-list")
                    || path.equals("/api/v1/notifications")
                    || (!get && !path.startsWith("/api/v1/auth/email-otp/") && !path.startsWith("/api/v1/auth/otp/"));
            operation.setSecurity(authenticated ? List.of(new SecurityRequirement().addList("bearerAuth")) : List.of());
            operation.addExtension("x-required-role", ticketScan ? "ADMIN_OR_VERIFIER" : verifier ? "VERIFIER" : admin ? "ADMIN" : authenticated ? "AUTHENTICATED" : "PUBLIC");
            operation.setDescription(operation.getDescription() + " Access: "
                    + (ticketScan ? "ADMIN or VERIFIER role required; verifiers must be assigned to the show." : verifier ? "Enabled VERIFIER account required." : admin ? "ADMIN role required." : authenticated ? "Authenticated account required." : "Public."));
            var responses = operation.getResponses();
            responses.putIfAbsent("400", error("Invalid request, malformed JSON, invalid parameter or failed validation."));
            if (authenticated) responses.putIfAbsent("401", error("Authentication required or token invalid/expired."));
            if (authenticated) responses.putIfAbsent("403", error("Access denied: insufficient role, disabled account or ownership restriction."));
            responses.putIfAbsent("404", error("Referenced resource not found, when applicable."));
            responses.putIfAbsent("409", error("Business conflict, duplicate data or unavailable seats, when applicable."));
            responses.putIfAbsent("500", error("Unexpected server failure."));
            if (path.startsWith("/api/v1/auth/email-otp/") || path.startsWith("/api/v1/auth/otp/")) {
                responses.put("429", error("OTP request or verification rate limit exceeded, when applicable."));
                responses.put("500", new ApiResponse().description("OTP processing unavailable.")
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().type("object")
                                        .addProperty("success", new Schema<>().type("boolean").example(false))
                                        .addProperty("message", new Schema<>().type("string").example("Unable to send OTP. Please try again later."))))));
            }
        }));
        };
    }

    private static ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrors"))));
    }
}
