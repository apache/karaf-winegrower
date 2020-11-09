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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConfigurationAdminTest {

    private final DefaultConfigurationAdmin configurationAdmin = new DefaultConfigurationAdmin(emptyMap(), emptyList()) {
        @Override
        protected ServiceReference<ConfigurationAdmin> getSelfReference() { // not needed for this tests
            return new ServiceReferenceImpl<>(new Hashtable<>(), null, null);
        }
    };

    @Test
    @DisplayName("ConfigurationAdmin should be able to use implicitly the environment")
    void envVarOverride(final TestInfo info) {
        final List<ConfigurationListener> listeners = new ArrayList<>();
        final DefaultConfigurationAdmin configurationAdmin = new DefaultConfigurationAdmin(emptyMap(), listeners) {
            @Override
            protected ServiceReference<ConfigurationAdmin> getSelfReference() { // not needed for this tests
                return new ServiceReferenceImpl<>(new Hashtable<>(), null, null);
            }
        };
        final Configuration java = configurationAdmin.getConfiguration("a.b.c");
        assertEquals("dummy", java.getProperties().get("fooBar"));
        assertEquals("set", java.getProperties().get("simple"));
    }

    @Test
    @DisplayName("Configuration creation can be forced - ConfigurationListener case")
    void preload(final TestInfo info) {
        final List<ConfigurationListener> listeners = new ArrayList<>();
        final DefaultConfigurationAdmin configurationAdmin = new DefaultConfigurationAdmin(emptyMap(), listeners) {
            @Override
            protected ServiceReference<ConfigurationAdmin> getSelfReference() { // not needed for this tests
                return new ServiceReferenceImpl<>(new Hashtable<>(), null, null);
            }
        };
        final String pid = info.getTestClass().orElseThrow(IllegalStateException::new).getName() + "." +
                info.getTestMethod().orElseThrow(IllegalStateException::new).getName() + ".pid";
        final String key = "foo";
        System.setProperty(pid + "." + key, "set");
        final AtomicBoolean called = new AtomicBoolean();
        listeners.add(event -> called.set(true));
        configurationAdmin.preload(singletonList(pid));
        System.clearProperty(pid + "." + key);
        assertTrue(called.get());
    }

    @Test
    @DisplayName("Should return value from system property")
    void systemPropertiesTest() {
        System.setProperty("winegrower.service.test.foo", "bar");
        Configuration configuration = configurationAdmin.getConfiguration("test");
        Assertions.assertEquals("bar", configuration.getProperties().get("foo"));
    }

    @Test
    @DisplayName("Should return value from cfg file in classpath")
    void externalConfigClasspathTest() {
        Configuration configuration = configurationAdmin.getConfiguration("external.test");
        Assertions.assertEquals("bar", configuration.getProperties().get("foo"));
    }

    @Test
    @DisplayName("Should return value from cfg file in winegrower.config.path location")
    void externalConfigPathTest() {
        File file = new File("src/test/resources");
        System.out.println(file.getAbsolutePath());
        System.setProperty("winegrower.config.path", "src/test/resources");
        Configuration configuration = configurationAdmin.getConfiguration("external.test");
        Assertions.assertEquals("bar", configuration.getProperties().get("foo"));
    }

}
