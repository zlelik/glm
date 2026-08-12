package info.gamed.glm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import info.gamed.glm.dto.PlayerDto;
import info.gamed.glm.entity.Player;
import info.gamed.glm.mapper.GlmMapper;
import info.gamed.glm.repository.PlayerRepository;


/**
 * REST api for the current player: GET /api/player/me. Returns a {@link PlayerDto} (id + nick name),
 * not the Player entity.
 * @author Z@
 */
@RestController
@RequestMapping("/api/player")
public class PlayerController {

    private final PlayerRepository playerRepository;
    private final GlmMapper mapper;

    public PlayerController(PlayerRepository playerRepository, GlmMapper mapper) {
        this.playerRepository = playerRepository;
        this.mapper = mapper;
    }

    @GetMapping("/me")
    public ResponseEntity<PlayerDto> getLoggedInPlayer(Authentication authentication) {
        String playerName = authentication.getName(); // Get the logged-in player's loginname
        Player currentPlayer = playerRepository.findByLoginName(playerName);
        if (currentPlayer != null) {
            return ResponseEntity.ok(mapper.toPlayerDto(currentPlayer));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
