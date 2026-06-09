package app.echo.next;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class StreamingGatewayControllerTest {
    @Test
    public void configureRepositoryDelegatesOnlyRepositoryWiring() {
        FakeEndpointStore endpointStore = new FakeEndpointStore();
        FakeViewModelBridge viewModelBridge = new FakeViewModelBridge();
        FakeListener listener = new FakeListener();
        StreamingGatewayController controller = new StreamingGatewayController(
                endpointStore,
                viewModelBridge,
                listener
        );

        controller.configureRepository();

        assertEquals(1, viewModelBridge.configureCount);
        assertEquals(0, viewModelBridge.refreshCount);
        assertEquals(0, listener.appliedEndpoints.size());
    }

    @Test
    public void applyEndpointPersistsNormalizesReconfiguresRefreshesAndNotifies() {
        FakeEndpointStore endpointStore = new FakeEndpointStore();
        FakeViewModelBridge viewModelBridge = new FakeViewModelBridge();
        FakeListener listener = new FakeListener();
        StreamingGatewayController controller = new StreamingGatewayController(
                endpointStore,
                viewModelBridge,
                listener
        );

        controller.applyEndpoint(" http://127.0.0.1:43990/// ");

        assertEquals("http://127.0.0.1:43990", endpointStore.endpoint());
        assertTrue(endpointStore.configured());
        assertEquals(1, viewModelBridge.configureCount);
        assertEquals(1, viewModelBridge.refreshCount);
        assertEquals("configure,refresh", viewModelBridge.events());
        assertEquals(1, listener.appliedEndpoints.size());
        assertEquals("http://127.0.0.1:43990", listener.appliedEndpoints.get(0));
    }

    @Test
    public void applyInvalidEndpointFallsBackToUnconfiguredEndpoint() {
        FakeEndpointStore endpointStore = new FakeEndpointStore();
        FakeViewModelBridge viewModelBridge = new FakeViewModelBridge();
        FakeListener listener = new FakeListener();
        StreamingGatewayController controller = new StreamingGatewayController(
                endpointStore,
                viewModelBridge,
                listener
        );

        controller.applyEndpoint("file:///tmp/gateway");

        assertEquals(StreamingGatewaySettingsStore.UNCONFIGURED_ENDPOINT, endpointStore.endpoint());
        assertEquals(1, viewModelBridge.configureCount);
        assertEquals(1, viewModelBridge.refreshCount);
        assertEquals(StreamingGatewaySettingsStore.UNCONFIGURED_ENDPOINT, listener.appliedEndpoints.get(0));
    }

    private static final class FakeEndpointStore implements StreamingGatewayEndpointStore {
        private String endpoint = StreamingGatewaySettingsStore.UNCONFIGURED_ENDPOINT;

        @Override
        public String endpoint() {
            return endpoint;
        }

        @Override
        public boolean configured() {
            return endpoint.startsWith("http://") || endpoint.startsWith("https://");
        }

        @Override
        public void setEndpoint(String nextEndpoint) {
            endpoint = StreamingGatewaySettingsStore.normalize(nextEndpoint);
        }
    }

    private static final class FakeViewModelBridge implements StreamingGatewayController.ViewModelBridge {
        private final ArrayList<String> events = new ArrayList<>();
        private int configureCount;
        private int refreshCount;

        @Override
        public void configureStreamingRepository() {
            configureCount++;
            events.add("configure");
        }

        @Override
        public void refreshStreamingProviders() {
            refreshCount++;
            events.add("refresh");
        }

        private String events() {
            return String.join(",", events);
        }
    }

    private static final class FakeListener implements StreamingGatewayController.Listener {
        private final ArrayList<String> appliedEndpoints = new ArrayList<>();

        @Override
        public void onStreamingGatewayApplied(String endpoint) {
            appliedEndpoints.add(endpoint);
        }
    }
}
