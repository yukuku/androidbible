package yuku.alkitab.base.services

/**
 * App-level container for the narrow service interfaces onto
 * [yuku.alkitab.base.S]. New code should depend on these interfaces via
 * [yuku.alkitab.base.App.services] rather than reaching into
 * [yuku.alkitab.base.S] directly.
 *
 * In production, all three interfaces are implemented by [yuku.alkitab.base.S].
 * In tests, callers can construct an [AppServices] with fake/in-memory implementations.
 */
class AppServices(
    @JvmField val storage: StorageProvider,
    @JvmField val versions: VersionManager,
    @JvmField val uiDimensions: UiDimensionsProvider,
)
