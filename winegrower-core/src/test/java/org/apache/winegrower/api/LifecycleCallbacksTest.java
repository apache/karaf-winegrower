package org.apache.winegrower.api;

import org.apache.winegrower.Ripener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LifecycleCallbacksTest {
    @Test
    void lifecycle() {
        final MyCallback callback = new MyCallback();
        final Ripener.Configuration configuration = new Ripener.Configuration();
        configuration.setLifecycleCallbacks(singletonList(callback));
        System.setProperty("winegrower.scanner.standalone.skipUrlsScanning", "true");
        try (final Ripener ripener = Ripener.create(configuration).start()) {
            // no-op
        } finally {
            System.clearProperty("winegrower.scanner.standalone.skipUrlsScanning");
        }
        assertEquals(
                asList("processConfiguration=true", "beforeStart=true", "afterStart=true", "beforeStop=true", "afterStop=true"),
                callback.events);
    }

    public static class MyCallback implements LifecycleCallbacks {
        private final List<String> events = new ArrayList<>();

        @Override
        public void processConfiguration(final Ripener.Configuration configuration) {
            events.add("processConfiguration=" + (configuration != null));
        }

        @Override
        public void beforeStart(final Ripener ripener) {
            events.add("beforeStart=" + (ripener != null));
        }

        @Override
        public void afterStart(final Ripener ripener) {
            events.add("afterStart=" + (ripener != null));
        }

        @Override
        public void beforeStop(final Ripener ripener) {
            events.add("beforeStop=" + (ripener != null));
        }

        @Override
        public void afterStop(final Ripener ripener) {
            events.add("afterStop=" + (ripener != null));
        }
    }
}
