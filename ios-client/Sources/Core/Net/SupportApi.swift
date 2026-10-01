import Foundation

/// One Help Centre article. List cards omit `body` and fill `preview`; detail has full body.
struct HelpArticleData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var title = ""
    var body = ""
    var preview = ""
    var likeCount = 0
    var dislikeCount = 0
    var myVote = 0
    var sortOrder = 0
    var status = 0

    private enum CodingKeys: String, CodingKey {
        case id, title, body, preview, likeCount, dislikeCount, myVote, sortOrder, status
    }

    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        title = c.str(.title)
        body = c.str(.body)
        preview = c.str(.preview)
        if preview.isEmpty, !body.isEmpty {
            preview = body.count > 160 ? String(body.prefix(160)) + "…" : body
        }
        likeCount = Int(c.int64(.likeCount))
        dislikeCount = Int(c.int64(.dislikeCount))
        myVote = Int(c.int64(.myVote))
        sortOrder = Int(c.int64(.sortOrder))
        status = Int(c.int64(.status))
    }
}

/// One "Report a problem" ticket.
struct SupportTicketData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var subject = ""
    var body = ""
    var status = ""
    var attachmentKeys: [String] = []
    var createdAt = ""
    var updatedAt = ""
    var replyCount = 0

    private enum CodingKeys: String, CodingKey {
        case id, subject, body, status, attachmentKeys, createdAt, updatedAt, replyCount
    }

    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        subject = c.str(.subject)
        body = c.str(.body)
        status = c.str(.status)
        attachmentKeys = (try? c.decodeIfPresent([String].self, forKey: .attachmentKeys)) ?? []
        createdAt = c.str(.createdAt)
        updatedAt = c.str(.updatedAt)
        replyCount = Int(c.int64(.replyCount))
    }
}

/// One reply on a support ticket thread.
struct SupportReplyData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var body = ""
    var isStaff = false
    var createdAt = ""
    var username = ""

    private enum CodingKeys: String, CodingKey {
        case id, body, isStaff, createdAt, username
    }

    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        body = c.str(.body)
        isStaff = c.bool(.isStaff)
        createdAt = c.str(.createdAt)
        username = c.str(.username)
    }
}

struct SupportTicketDetail: Decodable, Equatable {
    var ticket = SupportTicketData()
    var replies: [SupportReplyData] = []

    private enum CodingKeys: String, CodingKey { case ticket, replies }

    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ticket = (try? c.decodeIfPresent(SupportTicketData.self, forKey: .ticket)) ?? SupportTicketData()
        replies = (try? c.decodeIfPresent([SupportReplyData].self, forKey: .replies)) ?? []
    }
}

private struct SupportMediaKey: Decodable {
    var key = ""
    private enum CodingKeys: String, CodingKey { case key }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = c.str(.key)
    }
}

/// Help Centre articles and Report-a-problem tickets.
struct SupportApi {
    let api: ApiClient

    func articles() async -> ApiResponse<[HelpArticleData]> {
        await api.get("/v1/help/articles", authenticated: true, as: ItemsResponse<HelpArticleData>.self)
            .map(\.items)
    }

    func article(_ id: Int64) async -> ApiResponse<HelpArticleData> {
        await api.get("/v1/help/articles/\(id)", authenticated: true, as: HelpArticleData.self)
    }

    /// `vote`: 1 like, -1 dislike, 0 clear. Sending the same vote again clears it client-side.
    func vote(_ id: Int64, vote: Int) async -> ApiResponse<HelpArticleData> {
        await api.post(
            "/v1/help/articles/\(id)/vote",
            body: ["vote": vote],
            authenticated: true,
            as: HelpArticleData.self
        )
    }

    /// Raw image bytes for a ticket attachment. Server returns a storage `key`.
    func uploadTicketMedia(_ bytes: Data, ext: String = "jpg", contentType: String = "image/jpeg") async -> ApiResponse<String> {
        let safe = ext.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ext
        return await api.putBytes(
            "/v1/support/tickets/media?ext=\(safe)",
            body: bytes,
            contentType: contentType,
            as: SupportMediaKey.self
        ).map(\.key)
    }

    func createTicket(subject: String, body: String, keys: [String]) async -> ApiResponse<SupportTicketData> {
        await api.post(
            "/v1/support/tickets",
            body: [
                "subject": subject,
                "body": body,
                "attachment_keys": keys,
            ],
            authenticated: true,
            as: SupportTicketData.self
        )
    }

    func myTickets() async -> ApiResponse<[SupportTicketData]> {
        await api.get("/v1/support/tickets", authenticated: true, as: ItemsResponse<SupportTicketData>.self)
            .map(\.items)
    }

    func ticket(_ id: Int64) async -> ApiResponse<SupportTicketDetail> {
        await api.get("/v1/support/tickets/\(id)", authenticated: true, as: SupportTicketDetail.self)
    }

    func reply(_ id: Int64, body: String) async -> ApiResponse<SupportReplyData> {
        await api.post(
            "/v1/support/tickets/\(id)/replies",
            body: ["body": body],
            authenticated: true,
            as: SupportReplyData.self
        )
    }

    func legalArticles() async -> ApiResponse<[HelpArticleData]> {
        await api.get("/v1/legal/articles", authenticated: true, as: ItemsResponse<HelpArticleData>.self)
            .map(\.items)
    }

    func legalArticle(_ id: Int64) async -> ApiResponse<HelpArticleData> {
        await api.get("/v1/legal/articles/\(id)", authenticated: true, as: HelpArticleData.self)
    }

    func legalVote(_ id: Int64, vote: Int) async -> ApiResponse<HelpArticleData> {
        await api.post(
            "/v1/legal/articles/\(id)/vote",
            body: ["vote": vote],
            authenticated: true,
            as: HelpArticleData.self
        )
    }
}
