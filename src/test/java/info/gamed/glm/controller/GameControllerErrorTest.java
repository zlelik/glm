package info.gamed.glm.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Verifies that the REST API maps a missing game to an RFC 7807 ProblemDetail (404), rather than a raw
 * 500 / stack trace - i.e. that GameNotFoundException + GlobalExceptionHandler work end to end.
 *
 * MockMvc is built manually from the web context (Spring Boot 4 dropped @AutoConfigureMockMvc) and wired
 * with the Spring Security filter chain so the protected endpoint is reachable for an authenticated user.
 */
@SpringBootTest
class GameControllerErrorTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser // authenticated user so the request reaches the controller (the endpoint is protected)
    void unknownGameIdReturnsProblemDetail404() throws Exception {
        mockMvc.perform(get("/api/games/{id}/details", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value(containsString("999999")));
    }
}
