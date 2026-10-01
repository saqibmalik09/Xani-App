package app.xani.feature.main

enum class MainTab {
    Home,
    Games,
    Create,
    Inbox,
    Profile,
    ;

    val cinematic: Boolean
        get() = this == Home || this == Games || this == Create

    val lightSurface: Boolean
        get() = this == Profile || this == Inbox
}
