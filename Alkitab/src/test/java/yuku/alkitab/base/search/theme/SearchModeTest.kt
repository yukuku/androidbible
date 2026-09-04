package yuku.alkitab.base.search.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchModeTest {
    @Test fun `exact search remains the default`() {
        assertEquals(SearchMode.Exact, SearchMode.restore(null))
        assertEquals(SearchMode.Exact, SearchMode.restore("unknown"))
    }

    @Test fun `theme mode restores by stable name`() {
        assertEquals(SearchMode.ThemeOffline, SearchMode.restore("ThemeOffline"))
    }
}
