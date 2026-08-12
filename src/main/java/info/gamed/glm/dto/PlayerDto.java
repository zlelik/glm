package info.gamed.glm.dto;

/**
 * Public view of a player (e.g. the response of GET /api/player/me). Exposes only what clients need;
 * sensitive/internal fields (password, roles, loginName, registeredDate) are deliberately omitted.
 */
public record PlayerDto(Long id, String nickName) {
}
