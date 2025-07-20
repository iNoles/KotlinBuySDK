package com.github.inoles.shopifygraphqlauth

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SHOP_DOMAIN = "shopDomain"
private const val ACCESS_TOKEN = "access_token"
private val ENDPOINT_URL = "https://$SHOP_DOMAIN/api/2025-01/graphql".toHttpUrl()

class GraphClientBuilderTest {
    @Test
    fun buildSuccessWithCustomOkHttpClient() {
        val okHttpClient = OkHttpClient.Builder().build()

        val graphClient =
            GraphClient
                .Builder(
                    shopDomain = SHOP_DOMAIN,
                    accessToken = ACCESS_TOKEN,
                    apiVersion = "2025-01",
                ).httpClient(okHttpClient)
                .build()
        with(graphClient) {
            assertEquals(ENDPOINT_URL, serverUrl)
            assertNotNull(httpCallFactory)
            assertTrue(httpCallFactory is OkHttpClient)
        }
    }

    @Test
    fun buildSuccessWithDefaultClient() {
        val graphClient =
            GraphClient
                .Builder(
                    shopDomain = SHOP_DOMAIN,
                    accessToken = ACCESS_TOKEN,
                    apiVersion = "2025-01",
                ).build()
        with(graphClient) {
            assertEquals(ENDPOINT_URL, serverUrl)
            assertNotNull(httpCallFactory)
            assertTrue(httpCallFactory is OkHttpClient)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildFailWithEmptyShopDomain() {
        GraphClient.Builder(shopDomain = "", accessToken = ACCESS_TOKEN, apiVersion = "2025-01").build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildFailWithEmptyAccessToken() {
        GraphClient.Builder(shopDomain = SHOP_DOMAIN, accessToken = "", apiVersion = "2025-01").build()
    }
}
