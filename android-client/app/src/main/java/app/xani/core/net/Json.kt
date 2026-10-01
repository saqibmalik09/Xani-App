package app.xani.core.net

import org.json.JSONObject

/**
 * Total accessors over `org.json` (part of the platform, so no dependency and no
 * reflection). Every getter returns a default instead of throwing, because a field
 * the server renames must degrade one screen, not crash the app.
 */
internal fun JSONObject.str(key: String, fallback: String = ""): String =
    if (isNull(key)) fallback else optString(key, fallback)

internal fun JSONObject.int(key: String, fallback: Int = 0): Int = optInt(key, fallback)

internal fun JSONObject.long(key: String, fallback: Long = 0L): Long = optLong(key, fallback)

internal fun JSONObject.bool(key: String, fallback: Boolean = false): Boolean =
    optBoolean(key, fallback)

internal fun JSONObject.obj(key: String): JSONObject? =
    if (isNull(key)) null else optJSONObject(key)

/** Ids cross the wire as strings (they exceed 2^53); parse defensively either way. */
internal fun JSONObject.id(key: String): Long =
    when {
        isNull(key) -> 0L
        else -> optString(key).toLongOrNull() ?: optLong(key, 0L)
    }

internal fun JSONObject.strings(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return List(array.length()) { array.optString(it) }.filter { it.isNotEmpty() }
}

internal fun JSONObject.objects(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

/** Builds a request body, skipping nulls so optional fields are simply absent. */
internal fun jsonOf(vararg pairs: Pair<String, Any?>): JSONObject {
    val json = JSONObject()
    for ((key, value) in pairs) {
        if (value != null) json.put(key, value)
    }
    return json
}
