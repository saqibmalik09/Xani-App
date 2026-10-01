package app.xani.feature.home

/**
 * Live-lane reference data: the country filter. Rooms come from [LiveDirectory];
 * promo banners under the media/country filters come from [BannerStore]; the For
 * You feed lives in [FeedRepo].
 */
object FeedStore {

    val countries: List<LiveCountry> = listOf(
        LiveCountry("ALL", "🌍", "Worldwide"),
        LiveCountry("US", "🇺🇸", "USA"),
        LiveCountry("GB", "🇬🇧", "UK"),
        LiveCountry("AE", "🇦🇪", "UAE"),
        LiveCountry("SA", "🇸🇦", "Saudi"),
        LiveCountry("FR", "🇫🇷", "France"),
        LiveCountry("DE", "🇩🇪", "Germany"),
        LiveCountry("BR", "🇧🇷", "Brazil"),
        LiveCountry("IN", "🇮🇳", "India"),
        LiveCountry("JP", "🇯🇵", "Japan"),
        LiveCountry("KR", "🇰🇷", "Korea"),
        LiveCountry("NG", "🇳🇬", "Nigeria"),
        LiveCountry("MX", "🇲🇽", "Mexico"),
        LiveCountry("CA", "🇨🇦", "Canada"),
        LiveCountry("AU", "🇦🇺", "Australia"),
        LiveCountry("TR", "🇹🇷", "Turkey"),
        LiveCountry("PK", "🇵🇰", "Pakistan"),
        LiveCountry("EG", "🇪🇬", "Egypt"),
        LiveCountry("ID", "🇮🇩", "Indonesia"),
        LiveCountry("PH", "🇵🇭", "Philippines"),
        LiveCountry("TH", "🇹🇭", "Thailand"),
        LiveCountry("IT", "🇮🇹", "Italy"),
        LiveCountry("ES", "🇪🇸", "Spain"),
        LiveCountry("RU", "🇷🇺", "Russia"),
        LiveCountry("ZA", "🇿🇦", "S. Africa"),
    )
}
