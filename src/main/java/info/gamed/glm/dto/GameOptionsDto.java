package info.gamed.glm.dto;

import java.util.List;

/**
 * Options for the create/join forms (GET /api/games/options): the allowed colour palette and board sizes
 * plus their defaults. Served from the server so the palette/sizes have a single source of truth that the
 * server also validates submissions against.
 */
public record GameOptionsDto(
        List<ColorOptionDto> colors,
        String defaultColor,
        List<GameSizeDto> sizes,
        GameSizeDto defaultSize) {
}
