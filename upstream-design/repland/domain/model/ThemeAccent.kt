package com.swan1127.repland.domain.model

/**
 * The brand identity the app is painted in.
 *
 * This is deliberately a *closed* set, not a free colour picker, and it deliberately says
 * nothing about light versus dark. The two axes are independent on purpose:
 *
 * - **Light/dark** ([com.swan1127.repland.data.AppearanceMode]) decides *how* surfaces are
 *   separated — in light mode a card is a brighter object on a darker page, in dark mode the
 *   same relationship holds with both ends moved down. That is a structural decision.
 * - **Accent** (this enum) decides *which family* the interactive colour and the second
 *   emphasis colour belong to. That is an identity decision.
 *
 * Keeping them orthogonal is what stops the matrix from exploding: three identities across two
 * modes is six schemes, but only three ramps have to be authored, because each ramp declares its
 * own light and dark variant of the same family (see `ui/theme/AccentPalette.kt`).
 *
 * **What a theme is not allowed to change.** The warm neutral ramp (sand page, paper card, warm
 * ink) and the error colour are shared by every theme. Two reasons, both load-bearing:
 *
 * 1. The depth of the whole UI rests on the page/card split and on the warm neutral ramp. A
 *    theme that re-tinted the neutrals would have to re-tune every contrast relationship in the
 *    app, and the "which surface is raised" signal would start differing per theme.
 * 2. The neutrals are the only colours that also exist *outside* Compose — `values-night/`,
 *    `values/colors.xml` (the launcher window background) and the home-screen widget. Those
 *    cannot read a runtime accent, so if the neutrals moved with the theme they would silently
 *    disagree with the app. Keeping them fixed keeps every one of those mirrors correct.
 *
 * The name is stored by [name], so renaming a constant is a storage migration: an unknown value
 * read back from an older or newer build falls back to [DEFAULT] rather than failing.
 */
enum class ThemeAccent {
    /** Warm sand neutrals with a leaf-green brand and a copper second emphasis. The original. */
    BAMBOO,

    /** The same warm neutrals, but the interactive colour is indigo. */
    INDIGO,

    /** The same warm neutrals, with a plum brand and a teal second emphasis. */
    PLUM,
    ;

    companion object {
        /** What an unknown, blank, or absent stored value resolves to. */
        val DEFAULT = BAMBOO

        /**
         * Reads a stored accent name.
         *
         * Anything unrecognised — a null from a fresh install, a name from a build that no longer
         * exists, a hand-edited preference — resolves to [DEFAULT]. The alternative (throwing, or
         * resolving to null) would make the theme a thing that can fail to load, for a setting
         * whose entire job is to be cosmetic.
         */
        fun fromStorage(raw: String?): ThemeAccent =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}
