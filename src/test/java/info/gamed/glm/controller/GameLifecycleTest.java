package info.gamed.glm.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * Exercises the create / find / join game flow over HTTP against the seeded demo players. The full app
 * context is booted (so the scheduler runs); assertions therefore check players and ids, not the live
 * cell counts which the game loop mutates once a game has two players.
 */
@SpringBootTest
class GameLifecycleTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void player1CreatesGameAndPlayer2Joins() throws Exception {
        // player1 starts with no game.
        mockMvc.perform(get("/api/games/my").with(user("player1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(nullValue()));

        // player1 creates a 20x20 game with one cell on their (left) half.
        String createBody = "{\"color\":\"#008800\",\"width\":20,\"height\":20,\"cells\":[{\"x\":5,\"y\":5}]}";
        String response = mockMvc.perform(post("/api/games").with(user("player1")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        long gameId = ((Number) JsonPath.read(response, "$.gameId")).longValue();

        // It is now player1's game; details show player1 set and player2 still empty (waiting).
        mockMvc.perform(get("/api/games/my").with(user("player1")))
                .andExpect(jsonPath("$.gameId").value((int) gameId));
        mockMvc.perform(get("/api/games/{id}/details", gameId).with(user("player1")))
                .andExpect(jsonPath("$.width").value(20))
                .andExpect(jsonPath("$.player1.id", notNullValue()))
                .andExpect(jsonPath("$.player2").value(nullValue()));

        // player2 sees it in the joinable list, then joins with a cell on their (right) half.
        mockMvc.perform(get("/api/games/joinable").with(user("player2")))
                .andExpect(jsonPath("$[?(@.gameId == " + gameId + ")]").exists());
        String joinBody = "{\"color\":\"#0000CC\",\"cells\":[{\"x\":15,\"y\":5}]}";
        mockMvc.perform(post("/api/games/{id}/join", gameId).with(user("player2")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(joinBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value((int) gameId));

        // The game now has both players.
        mockMvc.perform(get("/api/games/{id}/details", gameId).with(user("player1")))
                .andExpect(jsonPath("$.player1.id", notNullValue()))
                .andExpect(jsonPath("$.player2.id", notNullValue()));
    }

    @Test
    void createRejectsCellOnTheWrongHalf() throws Exception {
        // player3 tries to place a cell on the RIGHT half (x=15 of 20) when creating -> 400, no game created.
        String bad = "{\"color\":\"#008800\",\"width\":20,\"height\":20,\"cells\":[{\"x\":15,\"y\":5}]}";
        mockMvc.perform(post("/api/games").with(user("player3")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid game request"));
        mockMvc.perform(get("/api/games/my").with(user("player3")))
                .andExpect(jsonPath("$.gameId").value(nullValue()));
    }

    @Test
    void createRejectsCellClaimedForAnotherPlayer() throws Exception {
        // player4 places a cell that names a DIFFERENT player's id as owner -> 400, no game created.
        String bad = "{\"color\":\"#008800\",\"width\":20,\"height\":20,\"cells\":[{\"x\":5,\"y\":5,\"playerId\":999999}]}";
        mockMvc.perform(post("/api/games").with(user("player4")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid game request"));
        mockMvc.perform(get("/api/games/my").with(user("player4")))
                .andExpect(jsonPath("$.gameId").value(nullValue()));
    }
}
