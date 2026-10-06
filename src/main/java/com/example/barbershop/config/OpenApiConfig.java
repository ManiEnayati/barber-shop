package com.example.barbershop.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    public static final String SESSION_COOKIE_SCHEME = "sessionCookie";
    private static final Map<String, String> GENERATED_GENERIC_RESPONSES = Map.of(
            "400", "Bad Request",
            "403", "Forbidden",
            "404", "Not Found",
            "409", "Conflict",
            "429", "Too Many Requests"
    );

    @Bean
    public OpenAPI barberShopOpenApi(
            @Value("${barbershop.api.version:development}") String applicationVersion
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title("Barber Shop API")
                        .version(applicationVersion)
                        .description("""
                                Backend API for the Barber Shop marketplace, including authentication,
                                public discovery, Customer booking, Barber management, appointment
                                lifecycle, ratings, claims, reputation-related flows, and administration.

                                Authentication uses phone OTP. Successful OTP verification creates an
                                HTTP session and sets the JSESSIONID cookie. Browser clients should request
                                an OTP, verify it, retain the session cookie, and include credentials on
                                subsequent requests. Customer, Barber, and Admin identity is resolved from
                                that authenticated session, never from client-supplied identity fields.
                                """))
                .components(new Components().addSecuritySchemes(
                        SESSION_COOKIE_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("""
                                        HTTP session cookie set by the server after successful OTP
                                        verification. Browser clients should call OTP request, call OTP
                                        verify, store the returned cookie, and send later requests with
                                        credentials. This API does not use JWT or Bearer authentication.
                                        """)))
                .servers(List.of(new Server()
                        .url("/")
                        .description("Same origin as the served API contract.")))
                .tags(List.of(
                        tag("Authentication", "Phone OTP login and HTTP-session creation."),
                        tag("Account", "Operations for the currently authenticated user and Customer profile."),
                        tag("Public Discovery", "Anonymous Barber, service, availability, and blocked-time discovery."),
                        tag("Customer Appointments", "Customer-owned booking, lifecycle, no-show response, and rating operations."),
                        tag("Customer Claims", "Explicitly claim or reject eligible Barber-created Guest appointments."),
                        tag("Barber Profile", "Authenticated Barber profile and Barber application operations."),
                        tag("Barber Appointments", "Authenticated Barber appointment dashboard and lifecycle operations."),
                        tag("Barber Services", "Authenticated Barber service catalog management."),
                        tag("Barber Schedule", "Authenticated Barber weekly working schedule."),
                        tag("Barber Blocked Times", "Authenticated Barber blocked-time management."),
                        tag("Admin - Barber Applications", "ADMIN review of Barber applications."),
                        tag("Legacy Compatibility", "Deprecated historical booking-confirmation compatibility."),
                        tag("System", "Non-product system and demonstration endpoints.")
                ));
    }

    @Bean
    public OpenApiCustomizer securedOperationResponses() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    operation.getResponses().entrySet().removeIf(entry ->
                            GENERATED_GENERIC_RESPONSES.get(entry.getKey()) != null
                                    && GENERATED_GENERIC_RESPONSES.get(entry.getKey())
                                    .equals(entry.getValue().getDescription()));
                    if (operation.getSecurity() != null
                            && !operation.getSecurity().isEmpty()) {
                            operation.getResponses().addApiResponse("401",
                                    new io.swagger.v3.oas.models.responses.ApiResponse()
                                            .description("No authenticated HTTP session."));
                            operation.getResponses().addApiResponse("403",
                                    new io.swagger.v3.oas.models.responses.ApiResponse()
                                            .description("Authenticated caller lacks the required role, identity, or ownership."));
                    }
                }));
    }

    private Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
