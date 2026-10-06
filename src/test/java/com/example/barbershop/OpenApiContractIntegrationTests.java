package com.example.barbershop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractIntegrationTests {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace"
    );
    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "post /api/auth/otp/request",
            "post /api/auth/otp/verify",
            "get /api/barbers",
            "get /api/barbers/{barberId}/available-times",
            "get /api/barber-services",
            "get /api/blocked-times",
            "post /api/appointments/{appointmentId}/confirm",
            "get /api/hello"
    );

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode document;

    @BeforeEach
    void loadDocument() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        document = objectMapper.readTree(json);
    }

    @Test
    void documentsMetadataAndHttpSessionCookieAuthentication() {
        assertEquals("Barber Shop API", document.at("/info/title").asText());
        assertEquals("0.0.1-SNAPSHOT", document.at("/info/version").asText());
        assertNotEquals("@project.version@", document.at("/info/version").asText());
        assertEquals("/", document.at("/servers/0/url").asText());
        assertFalse(document.toString().contains("localhost"),
                "The static contract must not hardcode a local server URL");

        JsonNode scheme = document.at("/components/securitySchemes/sessionCookie");
        assertEquals("apiKey", scheme.path("type").asText());
        assertEquals("cookie", scheme.path("in").asText());
        assertEquals("JSESSIONID", scheme.path("name").asText());
    }

    @Test
    void documentsFrontendOrientedTags() {
        Set<String> tags = new HashSet<>();
        document.path("tags").forEach(tag -> tags.add(tag.path("name").asText()));

        assertTrue(tags.containsAll(Set.of(
                "Authentication",
                "Account",
                "Public Discovery",
                "Customer Appointments",
                "Customer Claims",
                "Barber Profile",
                "Barber Appointments",
                "Barber Services",
                "Barber Schedule",
                "Barber Blocked Times",
                "Admin - Barber Applications",
                "Legacy Compatibility",
                "System"
        )));
    }

    @Test
    void everyActiveOperationHasStableFrontendDocumentation() {
        Set<String> operationIds = new HashSet<>();
        int operationCount = 0;

        for (JsonNode path : document.path("paths")) {
            for (var field : path.properties()) {
                if (!HTTP_METHODS.contains(field.getKey())) {
                    continue;
                }
                operationCount++;
                JsonNode operation = field.getValue();
                String operationId = operation.path("operationId").asText();
                assertFalse(operationId.isBlank());
                assertTrue(operationIds.add(operationId), "Duplicate operationId: " + operationId);
                assertFalse(operation.path("summary").asText().isBlank(), operationId + " has no summary");
                assertFalse(operation.path("description").asText().isBlank(), operationId + " has no description");
                assertTrue(operation.path("tags").isArray() && !operation.path("tags").isEmpty(),
                        operationId + " has no tag");
                assertTrue(operation.path("responses").isObject() && !operation.path("responses").isEmpty(),
                        operationId + " has no responses");
            }
        }

        assertEquals(40, document.path("paths").size());
        assertEquals(47, operationCount);
        assertTrue(operationIds.containsAll(Set.of(
                "requestOtp",
                "verifyOtp",
                "getCurrentUser",
                "listPublicBarbers",
                "getAvailableTimes",
                "createCustomerAppointment",
                "listMyAppointments",
                "createBarberGuestAppointment",
                "markAppointmentNoShow",
                "approveBarberApplication",
                "confirmLegacyAppointment"
        )));
    }

    @Test
    void scopesSessionSecurityToEveryOperation() {
        document.path("paths").properties().forEach(pathEntry ->
                pathEntry.getValue().properties().stream()
                        .filter(methodEntry -> HTTP_METHODS.contains(methodEntry.getKey()))
                        .forEach(methodEntry -> {
                            String key = methodEntry.getKey() + " " + pathEntry.getKey();
                            if (PUBLIC_OPERATIONS.contains(key)) {
                                assertNoSecurity(pathEntry.getKey(), methodEntry.getKey());
                            } else {
                                assertSessionSecurity(pathEntry.getKey(), methodEntry.getKey());
                            }
                        }));
    }

    @Test
    void keepsClosedBarberCreationRouteOutOfContract() {
        JsonNode publicBarberPath = document.at("/paths/~1api~1barbers");
        assertTrue(publicBarberPath.has("get"));
        assertFalse(publicBarberPath.has("post"));
    }

    @Test
    void publicDiscoverySchemasExcludePrivateFields() {
        JsonNode publicBarberProperties = document.at(
                "/components/schemas/PublicBarberResponse/properties"
        );
        assertFalse(publicBarberProperties.has("phone"));

        JsonNode publicBlockedProperties = document.at(
                "/components/schemas/PublicBlockedTimeResponse/properties"
        );
        assertFalse(publicBlockedProperties.has("reason"));
        assertFalse(publicBlockedProperties.has("createdAt"));
    }

    @Test
    void strictBookingSchemasExposeOnlyClientControlledChoices() {
        JsonNode customerBooking = document.at(
                "/components/schemas/CustomerAppointmentBookingRequest/properties"
        );
        assertEquals(Set.of("barberId", "serviceId", "date", "time"),
                propertyNames(customerBooking));

        JsonNode barberBooking = document.at(
                "/components/schemas/BarberAppointmentBookingRequest/properties"
        );
        assertEquals(Set.of("serviceId", "guestName", "guestPhone", "date", "time"),
                propertyNames(barberBooking));
    }

    @Test
    void removesMechanicallyGeneratedErrorCodesFromUnrelatedOperations() {
        JsonNode publicListResponses = operation("/api/barbers", "get").path("responses");
        assertEquals(Set.of("200"), propertyNames(publicListResponses));

        JsonNode privateProfileResponses = operation("/api/me/barber", "get")
                .path("responses");
        assertEquals(Set.of("200", "401", "403"), propertyNames(privateProfileResponses));
    }

    @Test
    void documentsJsonMediaTypesWithoutOverridingPlainTextSystemResponse() {
        JsonNode booking = operation("/api/me/appointments", "post");
        assertTrue(booking.path("requestBody").path("content").has("application/json"));
        assertTrue(booking.path("responses").path("201").path("content")
                .has("application/json"));

        JsonNode hello = operation("/api/hello", "get");
        assertTrue(hello.path("responses").path("200").path("content")
                .has("text/plain"));
    }

    @Test
    void identifiesLegacyConfirmationAsDeprecatedCompatibilityOnly() {
        JsonNode operation = operation(
                "/api/appointments/{appointmentId}/confirm", "post"
        );
        assertEquals("confirmLegacyAppointment", operation.path("operationId").asText());
        assertTrue(operation.path("deprecated").asBoolean());
        assertTrue(containsText(operation.path("tags"), "Legacy Compatibility"));
        assertTrue(operation.path("description").asText().contains("historical PENDING"));
        assertTrue(operation.path("responses").has("429"));
    }

    @Test
    void documentsRepresentativeSuccessAndErrorContracts() {
        JsonNode otpRequestResponses = operation("/api/auth/otp/request", "post")
                .path("responses");
        assertTrue(otpRequestResponses.has("202"));
        assertTrue(otpRequestResponses.has("400"));
        assertTrue(otpRequestResponses.has("429"));

        JsonNode bookingResponses = operation("/api/me/appointments", "post")
                .path("responses");
        assertTrue(bookingResponses.has("201"));
        assertTrue(bookingResponses.has("400"));
        assertTrue(bookingResponses.has("401"));
        assertTrue(bookingResponses.has("403"));
        assertTrue(bookingResponses.has("404"));
        assertTrue(bookingResponses.has("409"));
    }

    @Test
    void yamlContractAlsoGenerates() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openapi:")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Barber Shop API")));
    }

    private JsonNode operation(String path, String method) {
        return document.path("paths").path(path).path(method);
    }

    private void assertNoSecurity(String path, String method) {
        JsonNode security = operation(path, method).path("security");
        assertTrue(security.isMissingNode() || (security.isArray() && security.isEmpty()),
                () -> method.toUpperCase() + " " + path + " must be public in OpenAPI");
    }

    private void assertSessionSecurity(String path, String method) {
        JsonNode security = operation(path, method).path("security");
        assertTrue(security.isArray() && !security.isEmpty());
        assertTrue(security.get(0).has("sessionCookie"));
    }

    private boolean containsText(JsonNode array, String expected) {
        for (JsonNode value : array) {
            if (expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> propertyNames(JsonNode object) {
        Set<String> names = new HashSet<>();
        object.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }
}
