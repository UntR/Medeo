package com.untr.medeo.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SourceCatalogTest {
    private val manifestAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(RemoteSourceManifest::class.java)

    @Test
    fun remoteSourceManifest_isDisabledForCurrentRelease() {
        assertFalse(REMOTE_SOURCE_MANIFEST_ENABLED)
    }

    @Test
    fun mergeVodSources_overridesBuiltinAndAppendsValidRemoteSources() {
        val builtin = listOf(
            VodSource(
                id = "dbzy",
                name = "Builtin",
                baseUrl = "https://builtin.example/api.php/provide/vod",
                defaultEnabled = true
            )
        )
        val remoteManifestJson = """
            {
              "version": 1,
              "sources": [
                {
                  "id": "dbzy",
                  "name": "Remote Override",
                  "base_url": "https://remote.example/api.php/provide/vod",
                  "default_enabled": true
                },
                {
                  "id": "custom1",
                  "name": "Custom Source",
                  "base_url": "https://custom.example/api.php/provide/vod",
                  "default_enabled": false
                }
              ]
            }
        """.trimIndent()

        val merged = mergeVodSources(
            builtinSources = builtin,
            remoteManifestJson = remoteManifestJson,
            manifestAdapter = manifestAdapter
        )

        assertEquals(listOf("dbzy", "custom1"), merged.map { it.id })
        assertEquals("Remote Override", merged[0].name)
        assertEquals("https://remote.example/api.php/provide/vod", merged[0].baseUrl)
        assertEquals("Custom Source", merged[1].name)
    }
}
