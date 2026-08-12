package info.gamed.glm.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import info.gamed.glm.dto.CellDto;
import info.gamed.glm.dto.GameDetailsDto;
import info.gamed.glm.dto.GamePlayerDto;
import info.gamed.glm.dto.PlayerDto;
import info.gamed.glm.entity.Cell;
import info.gamed.glm.entity.Game;
import info.gamed.glm.entity.Player;

/**
 * Maps JPA entities to the API DTOs. MapStruct generates the implementation (GlmMapperImpl, a Spring
 * @Component) at compile time - matching fields by name and reporting any unmapped target at build time.
 */
@Mapper(componentModel = "spring")
public interface GlmMapper {

    // id and nickName match by name.
    PlayerDto toPlayerDto(Player player);

    // The getters are getXPosition()/getYPosition(), so the JavaBeans property names are XPosition /
    // YPosition (the leading two upper-case letters are not decapitalized).
    @Mapping(target = "x", source = "XPosition")
    @Mapping(target = "y", source = "YPosition")
    @Mapping(target = "playerId", source = "player.id")
    CellDto toCellDto(Cell cell);

    @Mapping(target = "width", source = "gameXDimension")
    @Mapping(target = "height", source = "gameYDimension")
    // Each player's colour is a per-game property, so combine the Player with the game's colour. 'cells'
    // maps by name (List<Cell> -> List<CellDto> via toCellDto).
    @Mapping(target = "player1", expression = "java(toGamePlayer(game.getPlayer1(), game.getPlayer1Color()))")
    @Mapping(target = "player2", expression = "java(toGamePlayer(game.getPlayer2(), game.getPlayer2Color()))")
    GameDetailsDto toGameDetailsDto(Game game);

    default GamePlayerDto toGamePlayer(Player player, String color) {
        if (player == null) {
            return null;
        }
        return new GamePlayerDto(player.getId(), player.getNickName(), color);
    }
}
