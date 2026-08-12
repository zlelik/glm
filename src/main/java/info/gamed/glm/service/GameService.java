package info.gamed.glm.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import info.gamed.glm.dto.CellDto;
import info.gamed.glm.dto.ColorOptionDto;
import info.gamed.glm.dto.CreateGameRequest;
import info.gamed.glm.dto.GameOptionsDto;
import info.gamed.glm.dto.GamePlayerDto;
import info.gamed.glm.dto.GameResultDto;
import info.gamed.glm.dto.GameSummaryDto;
import info.gamed.glm.dto.ProfileDto;
import info.gamed.glm.dto.GameSizeDto;
import info.gamed.glm.dto.JoinGameRequest;
import info.gamed.glm.dto.JoinableGameDto;
import info.gamed.glm.dto.GameHubDto;
import info.gamed.glm.entity.Cell;
import info.gamed.glm.entity.Game;
import info.gamed.glm.entity.GameStatus;
import info.gamed.glm.entity.Player;
import info.gamed.glm.exception.GameConflictException;
import info.gamed.glm.exception.GameNotFoundException;
import info.gamed.glm.exception.InvalidGameRequestException;
import info.gamed.glm.repository.CellRepository;
import info.gamed.glm.repository.GameRepository;

/**
 * Game lifecycle: finding a player's game, creating a game, listing joinable games and joining one.
 * Detailed read-only game data is also served here (getDetailedGameById).
 * @author Z@
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    // Allowed player colours (value + display name): rainbow hues (two shades each) plus black; no
    // white/grey. Default is green. Served to the client (single source of truth) and validated on submit.
    private static final String DEFAULT_COLOR = "#008800";
    private static final List<ColorOptionDto> ALLOWED_COLORS = List.of(
            new ColorOptionDto("#CC0000", "red"),
            new ColorOptionDto("#FF6666", "light_red"),
            new ColorOptionDto("#CC6600", "orange"),
            new ColorOptionDto("#FF9933", "light_orange"),
            new ColorOptionDto("#CCCC00", "yellow"),
            new ColorOptionDto("#FFFF66", "light_yellow"),
            new ColorOptionDto("#008800", "green"),
            new ColorOptionDto("#66CC66", "light_green"),
            new ColorOptionDto("#0000CC", "blue"),
            new ColorOptionDto("#6666FF", "light_blue"),
            new ColorOptionDto("#3300AA", "indigo"),
            new ColorOptionDto("#7755DD", "light_indigo"),
            new ColorOptionDto("#9900CC", "violet"),
            new ColorOptionDto("#CC66FF", "light_violet"),
            new ColorOptionDto("#FF3399", "pink"),
            new ColorOptionDto("#000000", "black"));

    // The hex values, for validating submissions.
    private static final Set<String> ALLOWED_COLOR_VALUES =
            Set.copyOf(ALLOWED_COLORS.stream().map(ColorOptionDto::value).toList());

    // Allowed board sizes (cells). Default is 20x20.
    private static final GameSizeDto DEFAULT_SIZE = new GameSizeDto(20, 20, "20 × 20");
    private static final List<GameSizeDto> ALLOWED_SIZES = List.of(
            new GameSizeDto(10, 10, "10 × 10"),
            DEFAULT_SIZE,
            new GameSizeDto(30, 30, "30 × 30"),
            new GameSizeDto(40, 40, "40 × 40"),
            new GameSizeDto(16, 9, "16 × 9 (landscape)"),
            new GameSizeDto(32, 18, "32 × 18 (landscape)"),
            new GameSizeDto(48, 27, "48 × 27 (landscape)"),
            new GameSizeDto(9, 16, "9 × 16 (mobile)"),
            new GameSizeDto(18, 32, "18 × 32 (mobile)"),
            new GameSizeDto(27, 48, "27 × 48 (mobile)"),
            // Large boards so bigger known objects (guns) fit a player's half (half width >= 36 for a gun).
            new GameSizeDto(60, 60, "60 × 60"),
            new GameSizeDto(100, 100, "100 × 100"),
            new GameSizeDto(96, 54, "96 × 54 (landscape)"),
            new GameSizeDto(128, 72, "128 × 72 (landscape)"),
            new GameSizeDto(176, 99, "176 × 99 (landscape)"));

    private final GameRepository gameRepository;
    private final CellRepository cellRepository;

    public GameService(GameRepository gameRepository, CellRepository cellRepository) {
        this.gameRepository = gameRepository;
        this.cellRepository = cellRepository;
    }

    /** Detailed game information including cells. */
    public Game getDetailedGameById(Long id) {
        log.debug("getDetailedGameById: prepare game detailed data");
        Game game = gameRepository.findDetailedInfoById(id).orElseThrow(() -> new GameNotFoundException(id));
        log.debug("getDetailedGameById: fetch finished");
        return game;
    }

    /** The outcome of a game (status + winner), for the "you win/lose/draw" message after it ends. */
    public GameResultDto getResult(Long gameId) {
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        return new GameResultDto(game.getStatus(), game.getWinnerId());
    }

    /** The colour palette and board sizes (with defaults) offered by the create/join forms. */
    public GameOptionsDto getOptions() {
        return new GameOptionsDto(ALLOWED_COLORS, DEFAULT_COLOR, ALLOWED_SIZES, DEFAULT_SIZE);
    }

    /** The id of the player's active game (as player1 or player2), or a null id if they have none. */
    public GameHubDto getGameHub(Player player) {
        List<Game> games = gameRepository.findByPlayerId(player.getId());
        return new GameHubDto(games.isEmpty() ? null : games.get(0).getId());
    }

    /** Games waiting for a second player (excluding the player's own), for the "search existing game" list. */
    public List<JoinableGameDto> getJoinableGames(Player player) {
        return gameRepository.findJoinableGames(player.getId()).stream()
                .map(g -> new JoinableGameDto(g.getId(),
                        new GamePlayerDto(g.getPlayer1().getId(), g.getPlayer1().getNickName(), g.getPlayer1Color()),
                        g.getGameXDimension(), g.getGameYDimension()))
                .toList();
    }

    /**
     * The player's profile: aggregate win/loss/draw counts over their completed games plus the full match
     * history (newest first). Each game's result and opponent are computed from THIS player's perspective.
     * Only started games (with a second player) are included; ongoing games are listed but not counted.
     */
    public ProfileDto getProfile(Player player) {
        Long playerId = player.getId();

        // Aggregate counts come straight from the database so they remain correct once the match list is
        // paginated (they cover ALL the player's games, not just the page returned below). Lost is derived:
        // every finished game is exactly one of won / drawn / lost from this player's perspective.
        int played = (int) gameRepository.countFinishedByPlayerId(playerId);
        int won = (int) gameRepository.countWonByPlayerId(playerId);
        int drawn = (int) gameRepository.countDrawnByPlayerId(playerId);
        int lost = played - won - drawn;

        List<Game> games = gameRepository.findMatchesByPlayerId(playerId);
        List<GameSummaryDto> history = new ArrayList<>(games.size());
        for (Game game : games) {
            boolean isPlayer1 = game.getPlayer1() != null && game.getPlayer1().getId().equals(playerId);
            Player opponent = isPlayer1 ? game.getPlayer2() : game.getPlayer1();

            String result;
            if (game.getStatus() != GameStatus.FINISHED) {
                result = "ONGOING";
            } else {
                Long winnerId = game.getWinnerId();
                if (winnerId != null && winnerId.equals(playerId)) {
                    result = "WON";
                } else if (winnerId != null && winnerId == Game.WINNER_DRAW) {
                    result = "DRAW";
                } else {
                    // Any other finished outcome (opponent won, or the player exited) counts as a loss.
                    result = "LOST";
                }
            }
            history.add(new GameSummaryDto(game.getId(),
                    opponent != null ? opponent.getNickName() : null,
                    game.getGameXDimension(), game.getGameYDimension(),
                    game.getStatus(), result, game.getFinishDateTime()));
        }
        return new ProfileDto(player.getNickName(), played, won, lost, drawn, history);
    }

    /**
     * Creates a game owned by the given player (player2 still empty) with their colour, board size and the
     * cells they placed on the LEFT half. The game stays frozen until a second player joins.
     */
    @Transactional
    public Long createGame(Player creator, CreateGameRequest request) {
        requireNoExistingGame(creator);
        validateColor(request.color());
        validateSize(request.width(), request.height());
        List<CellDto> cells = sanitizeCells(request.cells(), request.width(), request.height(), true, creator.getId());

        Game game = new Game(creator.getNickName() + "'s game", request.width(), request.height(),
                request.color(), null, creator, null, "system", Game.BOUNDARY_CONDITION_PERIODIC);
        game.setCreationDateTime(Instant.now());
        gameRepository.save(game);
        saveCells(cells, game, creator);
        log.info("Player {} created game {} ({}x{})", creator.getId(), game.getId(), request.width(), request.height());
        return game.getId();
    }

    /**
     * The given player joins a waiting game as player2: sets their colour and the cells they placed on the
     * RIGHT half, then signals that the game has started so the creator's waiting screen can advance.
     */
    @Transactional
    public Long joinGame(Player joiner, Long gameId, JoinGameRequest request) {
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        if (game.getPlayer2() != null) {
            throw new GameConflictException("This game already has two players.");
        }
        if (game.getPlayer1().getId().equals(joiner.getId())) {
            throw new GameConflictException("You cannot join your own game.");
        }
        requireNoExistingGame(joiner);
        validateColor(request.color());
        if (request.color().equals(game.getPlayer1Color())) {
            throw new InvalidGameRequestException("You cannot use the same colour as the game creator.");
        }
        List<CellDto> cells = sanitizeCells(request.cells(), game.getGameXDimension(), game.getGameYDimension(), false, joiner.getId());

        game.setPlayer2(joiner);
        game.setPlayer2Color(request.color());
        game.setStatus(GameStatus.ACTIVE);
        game.setStartDateTime(Instant.now());
        // Both players start the live game with one credit so each can place a cell immediately; every
        // processed iteration then grants one more (see SingleGameProcessor#grantCredit).
        game.setPlayer1AccumulatedCells(1);
        game.setPlayer2AccumulatedCells(1);
        gameRepository.save(game);
        saveCells(cells, game, joiner);
        log.info("Player {} joined game {}", joiner.getId(), gameId);
        // The "game started" WebSocket notification is fired by the controller AFTER this transaction
        // commits, so the creator's refetch reads the committed (two-player) state, not a pre-commit one.
        return gameId;
    }

    /**
     * Ends a game on a player's request: marks it FINISHED and deletes all its cells (the game row is kept).
     * The caller must be one of the game's players. Idempotent if the game is already finished.
     */
    @Transactional
    public void exitGame(Player player, Long gameId) {
        log.debug("[GameService.exitGame()] Player {} ({}) exit game {}", player.getId(), player.getNickName(), gameId);
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        boolean participant = (game.getPlayer1() != null && game.getPlayer1().getId().equals(player.getId()))
                || (game.getPlayer2() != null && game.getPlayer2().getId().equals(player.getId()));
        if (!participant) {
            throw new GameConflictException("You are not a participant of this game.");
        }
        if (game.getStatus() == GameStatus.FINISHED) {
            log.debug("[GameService.exitGame()] Game {} is already finished.", gameId);
            return;
        }
        game.setStatus(GameStatus.FINISHED);
        Player opponentPlayer = game.getPlayer1() != null && game.getPlayer2() != null && !game.getPlayer1().getId().equals(player.getId()) ? game.getPlayer1() : game.getPlayer2();
        if (opponentPlayer != null) {
            log.debug("[GameService.exitGame()] opponentPlayer {} ({}) for the game {}", opponentPlayer.getId(), opponentPlayer.getNickName(), gameId);
            game.setWinnerId(opponentPlayer.getId());
            log.debug("[GameService.exitGame()] Winner is {}. loser is {} for the game {}", opponentPlayer.getId(), player.getId(), gameId);
        } else {
            log.debug("[GameService.exitGame()] No opponent player for the game {}. No winner/losser.", gameId);
        }
        game.setFinishDateTime(Instant.now());
        gameRepository.save(game);
        cellRepository.deleteByGameId(gameId);
        log.debug("[GameService.exitGame()] Player {} ({}) exited game {} (finished)", player.getId(), player.getNickName(), gameId);
    }

    /**
     * Places one or more live cells for the player during an active game (a single click = one cell; a known
     * object = its cells). Rules: the game must be ACTIVE and the player a participant; every cell must be in
     * bounds (the whole field is allowed, unlike the initial armies); already-occupied cells are skipped (and
     * not charged). Each newly
     * created cell costs one "accumulated cell" credit, so the player must have banked at least as many
     * credits as the number of cells actually placed - otherwise nothing is placed. The owner is set from
     * the session, never the request.
     * @return the ids of the created cells (to drive the WebSocket refresh); empty if nothing was placed.
     */
    @Transactional
    public List<Long> addCells(Player player, Long gameId, List<CellDto> requested) {
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        if (game.getStatus() != GameStatus.ACTIVE) {
            throw new GameConflictException("The game is not active.");
        }
        boolean isPlayer1 = game.getPlayer1() != null && game.getPlayer1().getId().equals(player.getId());
        boolean isPlayer2 = game.getPlayer2() != null && game.getPlayer2().getId().equals(player.getId());
        if (!isPlayer1 && !isPlayer2) {
            throw new GameConflictException("You are not a participant of this game.");
        }
        // Validate bounds/ownership and de-duplicate. During the LIVE game a player may place on ANY half of
        // the board (the whole field is fair game), so no half restriction here - unlike the initial armies.
        List<CellDto> sane = sanitizeCells(requested, game.getGameXDimension(), game.getGameYDimension(),
                null, player.getId());
        // Skip cells that are already occupied - they are not placed again and cost no credits.
        List<CellDto> toPlace = sane.stream()
                .filter(c -> !cellRepository.existsCellAt(gameId, c.x(), c.y()))
                .toList();
        if (toPlace.isEmpty()) {
            return List.of();
        }
        int cost = toPlace.size();
        int available = isPlayer1 ? game.getPlayer1AccumulatedCells() : game.getPlayer2AccumulatedCells();
        if (cost > available) {
            throw new GameConflictException(
                    "Not enough accumulated cells: need " + cost + ", have " + available + ".");
        }
        List<Long> createdIds = new ArrayList<>();
        for (CellDto c : toPlace) {
            // Owner is taken from the session, never c.playerId() (sanitizeCells already rejected mismatches).
            createdIds.add(cellRepository.save(new Cell(c.x(), c.y(), game, player)).getId());
        }
        if (isPlayer1) {
            game.setPlayer1AccumulatedCells(available - cost);
        } else {
            game.setPlayer2AccumulatedCells(available - cost);
        }
        gameRepository.save(game);
        log.info("Player {} placed {} cell(s) in game {} ({} credits left)", player.getId(), cost, gameId,
                available - cost);
        return createdIds;
    }

    // --- helpers -------------------------------------------------------------------------------------

    private void requireNoExistingGame(Player player) {
        if (!gameRepository.findByPlayerId(player.getId()).isEmpty()) {
            throw new GameConflictException("You already have an active game.");
        }
    }

    private void validateColor(String color) {
        if (color == null || !ALLOWED_COLOR_VALUES.contains(color)) {
            throw new InvalidGameRequestException("Unsupported colour: " + color);
        }
    }

    private void validateSize(int width, int height) {
        boolean known = ALLOWED_SIZES.stream().anyMatch(s -> s.width() == width && s.height() == height);
        if (!known) {
            throw new InvalidGameRequestException("Unsupported board size: " + width + "x" + height);
        }
    }

    /**
     * Validates the placed cells and returns them de-duplicated by position. Each cell must be inside the
     * board. When {@code requiredLeftHalf} is non-null the cell must also be on that half (player1 LEFT:
     * 2*x < width, player2 RIGHT: 2*x >= width) - used for the initial armies; pass null to allow the whole
     * field (live-game placement). Security: a cell may only name the current player as its owner - a request
     * that claims a cell for a DIFFERENT player is rejected. (The actual owner is still set from the session
     * in saveCells, never from the request, so this is a defence-in-depth guard on top of that.)
     */
    private List<CellDto> sanitizeCells(List<CellDto> cells, int width, int height, Boolean requiredLeftHalf, Long ownerId) {
        if (cells == null) {
            return List.of();
        }
        Set<String> seenPositions = new LinkedHashSet<>();
        List<CellDto> result = new ArrayList<>();
        for (CellDto c : cells) {
            if (c.x() < 0 || c.x() >= width || c.y() < 0 || c.y() >= height) {
                throw new InvalidGameRequestException("Cell out of bounds: (" + c.x() + ", " + c.y() + ")");
            }
            boolean inLeftHalf = 2 * c.x() < width;
            if (requiredLeftHalf != null && requiredLeftHalf != inLeftHalf) {
                throw new InvalidGameRequestException("Cell on the wrong half of the board: (" + c.x() + ", " + c.y() + ")");
            }
            if (c.playerId() != null && !c.playerId().equals(ownerId)) {
                throw new InvalidGameRequestException("A cell may only be placed for your own player.");
            }
            if (seenPositions.add(c.x() + "," + c.y())) {
                result.add(c);
            }
        }
        return result;
    }

    private void saveCells(List<CellDto> cells, Game game, Player owner) {
        for (CellDto c : cells) {
            // The owner is taken from the session (owner), NOT from c.playerId() - never trust the request
            // for ownership. sanitizeCells already rejected any cell that named a different player.
            cellRepository.save(new Cell(c.x(), c.y(), game, owner));
        }
    }
}
