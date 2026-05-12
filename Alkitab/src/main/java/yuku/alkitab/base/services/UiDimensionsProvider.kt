package yuku.alkitab.base.services

import yuku.alkitab.base.S

/**
 * Exposes the cached, preference-derived UI dimensions used for verse rendering.
 * Extracted from [S] so verse-rendering code can depend on a narrow interface.
 */
interface UiDimensionsProvider {
    fun applied(): S.CalculatedDimensions
    fun recalculate()
}
