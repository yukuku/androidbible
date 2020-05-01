package yuku.alkitab.base.util

object RequestCodes {
    object FromActivity {
        const val Goto = 1
        const val Share = 7
        const val TextAppearanceGetFonts = 9
        const val TextAppearanceCustomColors = 10
        const val EditNote1 = 11
        const val EditNote2 = 12

        const val PermissionSettings = 9970
    }
    object FromFragment

    object PermissionFromActivity {
        const val Storage = 1
    }
    object PermissionFromFragment
}
