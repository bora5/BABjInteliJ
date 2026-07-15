package rs.co.bora5.plugins.babj.model;

/**
 * Small naming helpers shared by the generator: humanising field names into labels, decapitalising
 * identifiers, and deriving the project's base package from an entity's package.
 */
public final class BabjNaming {

    private BabjNaming() {
    }

    /**
     * Turns a camelCase field name into a human label, e.g. {@code planiranoVremeUtovar} &rarr;
     * {@code "Planirano vreme utovar"}.
     */
    public static String label(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append(' ').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }

    /** Lower-cases the first character (e.g. {@code Firma} &rarr; {@code firma}). */
    public static String decapitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** Upper-cases the first character (e.g. {@code firma} &rarr; {@code Firma}). */
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Derives the project's base package from an entity's package: entities live in
     * {@code <base>.model}, so {@code rs.co.bora5.programs.wastex.model} &rarr;
     * {@code rs.co.bora5.programs.wastex}.
     */
    public static String basePackage(String entityPackage) {
        if (entityPackage == null) {
            return "";
        }
        if (entityPackage.endsWith(".model")) {
            return entityPackage.substring(0, entityPackage.length() - ".model".length());
        }
        return entityPackage;
    }
}
