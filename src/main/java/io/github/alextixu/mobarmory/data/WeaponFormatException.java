package io.github.alextixu.mobarmory.data;

/**
 * A weapon file or a web request does not follow the format (decision O1). Names the field that is wrong,
 * for example {@code modifiers[1].percent}, so the web form can mark that box (B10).
 */
public final class WeaponFormatException extends Exception {
    private final String field;

    public WeaponFormatException(String field, String message) {
        super(field.isEmpty() ? message : field + ": " + message);
        this.field = field;
    }

    /** The JSON path of the wrong field; empty when the whole document is wrong. */
    public String field() {
        return field;
    }

    /** The message without the field prefix. */
    public String reason() {
        String message = getMessage();
        return field.isEmpty() ? message : message.substring(field.length() + 2);
    }
}
