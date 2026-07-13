package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.function.Consumer
import java.util.function.Supplier

class StreamingProviderSettingsOwnerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun endpointChangePersistsReconfiguresAndPublishesStatus() {
        val store = InMemoryEndpointStore()
        val statuses = mutableListOf<String>()
        val owner = StreamingProviderSettingsOwner(
            store,
            StreamingViewModel(),
            StreamingRecommendationViewModel(),
            Supplier { AppLanguage.MODE_ENGLISH },
            Consumer { statuses += it }
        )

        owner.applyEndpoint("http://127.0.0.1:43990")

        assertEquals("http://127.0.0.1:43990", store.endpoint())
        assertEquals(
            listOf(
                AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.gateway.applied") +
                    "http://127.0.0.1:43990"
            ),
            statuses
        )
    }

    private class InMemoryEndpointStore : StreamingGatewayEndpointStore {
        private var value = StreamingGatewaySettingsStore.UNCONFIGURED_ENDPOINT

        override fun endpoint(): String = value

        override fun configured(): Boolean = value.startsWith("http")

        override fun setEndpoint(nextEndpoint: String?) {
            value = StreamingGatewaySettingsStore.normalize(nextEndpoint)
        }
    }
}
