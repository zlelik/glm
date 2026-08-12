package info.gamed.glm.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import info.gamed.glm.dto.CellDto;
import info.gamed.glm.dto.CreateGameRequest;
import info.gamed.glm.dto.GameDetailsDto;
import info.gamed.glm.dto.GameOptionsDto;
import info.gamed.glm.dto.GameResultDto;
import info.gamed.glm.dto.JoinGameRequest;
import info.gamed.glm.dto.JoinableGameDto;
import info.gamed.glm.dto.GameHubDto;
import info.gamed.glm.dto.ProfileDto;
import info.gamed.glm.entity.Player;
import info.gamed.glm.exception.UnauthenticatedException;
import info.gamed.glm.mapper.GlmMapper;
import info.gamed.glm.notification.CellChangeNotifier;
import info.gamed.glm.notification.GameNotifier;
import info.gamed.glm.repository.PlayerRepository;
import info.gamed.glm.service.GameService;

/**
 * REST controller for games: reading detailed game data, finding/creating/joining the current player's
 * game. Returns DTOs (the API contract), kept decoupled from the JPA entities.
 * @author Z@
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final PlayerRepository playerRepository;
    private final GameNotifier gameNotifier;
    private final CellChangeNotifier cellChangeNotifier;
    private final GlmMapper mapper;

    public GameController(GameService gameService, PlayerRepository playerRepository, GameNotifier gameNotifier,
                          CellChangeNotifier cellChangeNotifier, GlmMapper mapper) {
        this.gameService = gameService;
        this.playerRepository = playerRepository;
        this.gameNotifier = gameNotifier;
        this.cellChangeNotifier = cellChangeNotifier;
        this.mapper = mapper;
    }

    /** Detailed information about a game (board size, players with colours, and cells). */
    @GetMapping("/{id}/details")
    public GameDetailsDto getGameDetailsById(@PathVariable Long id) {
        return mapper.toGameDetailsDto(gameService.getDetailedGameById(id));
    }

    /** The colour palette and board sizes for the create/join forms. */
    @GetMapping("/options")
    public GameOptionsDto getOptions() {
        return gameService.getOptions();
    }

    /** The outcome of a game (status + winner), used to show the win/lose/draw message after it ends. */
    @GetMapping("/{id}/result")
    public GameResultDto getResult(@PathVariable Long id) {
        return gameService.getResult(id);
    }

    /** The current player's active game id (or a null id if they have none). */
    @GetMapping("/my")
    public GameHubDto getGameHub(Authentication authentication) {
        return gameService.getGameHub(currentPlayer(authentication));
    }

    /** The current player's profile: aggregate win/loss stats and their match history (newest first). */
    @GetMapping("/profile")
    public ProfileDto getProfile(Authentication authentication) {
        return gameService.getProfile(currentPlayer(authentication));
    }

    /** Games waiting for a second player that the current player can join. */
    @GetMapping("/joinable")
    public List<JoinableGameDto> getJoinableGames(Authentication authentication) {
        return gameService.getJoinableGames(currentPlayer(authentication));
    }

    /** Create a new game owned by the current player (player2 empty until someone joins). */
    @PostMapping
    public GameHubDto createGame(Authentication authentication, @RequestBody CreateGameRequest request) {
        return new GameHubDto(gameService.createGame(currentPlayer(authentication), request));
    }

    /** Join a waiting game as the second player. */
    @PostMapping("/{id}/join")
    public GameHubDto joinGame(Authentication authentication, @PathVariable Long id, @RequestBody JoinGameRequest request) {
        Long gameId = gameService.joinGame(currentPlayer(authentication), id, request);
        // Notify AFTER joinGame's transaction has committed (it has, since the call returned), so the
        // creator's "game started" refetch reads the committed two-player state rather than a stale one.
        gameNotifier.gameStarted(gameId);
        return new GameHubDto(gameId);
    }

    /**
     * Place one or more live cells during an active game: a single click sends one cell, a known object sends
     * its cells. Each placed cell costs one accumulated-cell credit; owner = session. Occupied cells are
     * skipped. Clients refetch the board on the resulting notification.
     */
    @PostMapping("/{id}/cells")
    public void addCells(Authentication authentication, @PathVariable Long id, @RequestBody List<CellDto> cells) {
        List<Long> createdIds = gameService.addCells(currentPlayer(authentication), id, cells);
        // Notify only if at least one cell was actually placed (the call has committed).
        if (!createdIds.isEmpty()) {
            cellChangeNotifier.cellCreated(createdIds.get(0));
        }
    }

    /** Exit (end) a game the current player is in: it becomes FINISHED and its cells are deleted. */
    @PostMapping("/{id}/exit")
    public GameHubDto exitGame(Authentication authentication, @PathVariable Long id) {
        gameService.exitGame(currentPlayer(authentication), id);
        // After the transaction has committed, tell the other player to leave the now-finished game.
        gameNotifier.gameFinished(id);
        return new GameHubDto(null);
    }

    private Player currentPlayer(Authentication authentication) {
        Player player = (authentication == null) ? null : playerRepository.findByLoginName(authentication.getName());
        if (player == null) {
            // Authenticated principal with no matching Player row (e.g. a stale session). Return 401 so the
            // client re-authenticates, rather than NPEing deeper in the service layer.
            throw new UnauthenticatedException("No player found for the current session.");
        }
        return player;
    }
}
