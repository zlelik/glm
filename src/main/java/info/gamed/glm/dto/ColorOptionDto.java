package info.gamed.glm.dto;

/**
 * A selectable player colour: the hex value used everywhere, plus a stable i18n key (e.g. "#66CC66" ->
 * "light_green"). The frontend translates the key (color_light_green) so colour names are localized
 * client-side - no backend i18n needed.
 */
public record ColorOptionDto(String value, String key) {
}
