package info.gamed.glm.dto;

/**
 * A selectable board size (in cells) with a human label for the dropdown, e.g. 32x18 -> "32 × 18 (landscape)".
 */
public record GameSizeDto(int width, int height, String label) {
}
