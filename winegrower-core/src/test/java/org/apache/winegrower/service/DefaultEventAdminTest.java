/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.winegrower.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.deployer.BundleImpl;
import org.junit.jupiter.api.Test;
import org.osgi.service.event.Event;

class DefaultEventAdminTest {

    @Test
    void publish() {
        final BundleRegistry registry = new BundleRegistry(new OSGiServices(null, emptyList(), emptyList()),
                new Ripener.Configuration());
        final BundleImpl bundle = registry.getBundles().values().iterator().next().getBundle(); // just to get one
        final List<DefaultEventAdmin.EventHandlerInstance> listeners = new ArrayList<>();
        final Collection<String> events = new ArrayList<>();
        try (final DefaultEventAdmin admin = new DefaultEventAdmin(listeners, 1)) {
            listeners.add(new DefaultEventAdmin.EventHandlerInstance(bundle, event -> events.add(asString(event)),
                    new String[] { "test" }, null));
            admin.sendEvent(new Event("test", singletonMap("winegrower", "1")));
            assertEquals(singletonList("@test: event.topics=test, winegrower=1"), events);
            admin.sendEvent(new Event("test/another", singletonMap("weingrower", "2")));
            assertEquals(2, events.size());

            listeners.add(new DefaultEventAdmin.EventHandlerInstance(bundle, event -> events.add(asString(event)),
                    new String[] { "test" }, "(&(winegrower=4)(event.topics=test))"));
            admin.sendEvent(new Event("test/no", singletonMap("weingrower", "3")));
            assertEquals(3, events.size());
            admin.sendEvent(new Event("test", singletonMap("weingrower", "4")));
            assertEquals(4, events.size());
            admin.sendEvent(new Event("test/single", singletonMap("weingrower", "4")));
            assertEquals(5, events.size());
        }
    }

    private String asString(final Event event) {
        return "@" + event.getTopic() + ": "
                + Stream.of(event.getPropertyNames()).sorted().map(it -> it + "=" + event.getProperty(it)).collect(joining(", "));
    }
}
