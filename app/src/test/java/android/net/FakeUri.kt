package android.net

import android.os.Parcel

internal class FakeUri(
    private val value: String
) : Uri() {
    override fun isHierarchical(): Boolean = true

    override fun isRelative(): Boolean = !value.contains(":")

    override fun getScheme(): String? = value.substringBefore(":", missingDelimiterValue = "")
        .ifEmpty { null }

    override fun getSchemeSpecificPart(): String? = value.substringAfter(":", missingDelimiterValue = "")
        .ifEmpty { null }

    override fun getEncodedSchemeSpecificPart(): String? = schemeSpecificPart

    override fun getAuthority(): String? = null

    override fun getEncodedAuthority(): String? = authority

    override fun getUserInfo(): String? = null

    override fun getEncodedUserInfo(): String? = userInfo

    override fun getHost(): String? = null

    override fun getPort(): Int = -1

    override fun getPath(): String? = value

    override fun getEncodedPath(): String? = path

    override fun getQuery(): String? = null

    override fun getEncodedQuery(): String? = query

    override fun getFragment(): String? = null

    override fun getEncodedFragment(): String? = fragment

    override fun getPathSegments(): List<String> = emptyList()

    override fun getLastPathSegment(): String? = null

    override fun toString(): String = value

    override fun buildUpon(): Builder = throw UnsupportedOperationException()

    override fun describeContents(): Int = 0

    override fun writeToParcel(out: Parcel, flags: Int) = Unit
}
