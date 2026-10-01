import Foundation

/// One promotional banner from GET /v1/banners.
struct BannerData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var title = ""
    var description = ""
    var imageKey = ""
    var placement = ""
    var sortOrder = 0

    private enum CodingKeys: String, CodingKey {
        case id, title, description, imageKey, placement, sortOrder
    }

    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        title = c.str(.title)
        description = c.str(.description)
        imageKey = c.str(.imageKey)
        placement = c.str(.placement)
        sortOrder = Int(c.int64(.sortOrder))
    }
}

/// Public banner list (no auth). Placement tags where the client shows them.
struct BannerApi {
    let api: ApiClient

    func list(placement: String, limit: Int = 20) async -> ApiResponse<[BannerData]> {
        let response = await api.get(
            "/v1/banners",
            query: ["placement": placement, "limit": String(limit)],
            authenticated: false,
            as: ItemsResponse<BannerData>.self
        )
        return response.map(\.items)
    }
}
