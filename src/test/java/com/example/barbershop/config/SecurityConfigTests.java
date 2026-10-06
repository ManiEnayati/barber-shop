package com.example.barbershop.config;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(SecurityConfigTests.UnclassifiedController.class)
@Import(SecurityConfig.class)
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unclassifiedEndpointIsDeniedToAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/unclassified"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unclassifiedEndpointIsDeniedToAuthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/unclassified")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalErrorDispatchCanReachMvcErrorHandling() throws Exception {
        mockMvc.perform(get("/api/unclassified")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        }))
                .andExpect(status().isNotFound());
    }

    @Test
    void directErrorRequestRemainsDenied() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class UnclassifiedController {

        @GetMapping("/api/unclassified")
        String get() {
            return "must not be public";
        }
    }
}
