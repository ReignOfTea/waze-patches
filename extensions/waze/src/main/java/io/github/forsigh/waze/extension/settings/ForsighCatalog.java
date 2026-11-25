package io.github.forsigh.waze.extension.settings;

/**
 * Patch-time catalogue of the rows the settings screen shows.
 *
 * The body is a placeholder. The settings patch replaces it with one encoded string built from the
 * patches that actually ran, so the screen only lists options whose hooks are in this APK.
 */
public final class ForsighCatalog {

    /** Replaced at patch time. Left as-is when no runtime option was registered. */
    public static final String PLACEHOLDER = "__FORSIGH_CATALOG__";

    private ForsighCatalog() {
    }

    public static String encoded() {
        return PLACEHOLDER;
    }
}
