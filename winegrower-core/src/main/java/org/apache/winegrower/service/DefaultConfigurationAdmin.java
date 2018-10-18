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

import static java.util.Collections.list;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

public class DefaultConfigurationAdmin implements ConfigurationAdmin {
    private final Map<String, Configuration> configurations = new HashMap<>();

    @Override
    public Configuration createFactoryConfiguration(final String pid) {
        return new DefaultConfiguration(pid, null, null);
    }

    @Override
    public Configuration createFactoryConfiguration(final String pid, final String location) {
        return new DefaultConfiguration(pid, null, location);
    }

    @Override
    public Configuration getConfiguration(final String pid, final String location) {
        return configurations.computeIfAbsent(pid, p -> new DefaultConfiguration(null, p, location));
    }

    @Override
    public Configuration getConfiguration(final String pid) {
        return configurations.computeIfAbsent(pid, p -> new DefaultConfiguration(null, p, null));
    }

    @Override
    public Configuration[] listConfigurations(final String filter) {
        try {
            final Filter predicate = filter == null ? null : FrameworkUtil.createFilter(filter);
            return configurations.values().stream()
                    .filter(it -> predicate == null || predicate.match(it.getProperties()))
                    .toArray(Configuration[]::new);
        } catch (final InvalidSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static class DefaultConfiguration implements Configuration {
        private final String factoryPid;
        private final String pid;
        private String location;
        private final Hashtable<String, Object> properties = new Hashtable<>();
        private final AtomicLong changeCount = new AtomicLong();

        private DefaultConfiguration(final String factoryPid, final String pid, final String location) {
            this.factoryPid = factoryPid;
            this.pid = pid;
            this.location = location;
            final String prefix = "winegrower.service." + pid + ".";
            System.getProperties().stringPropertyNames().stream()
                  .filter(it -> it.startsWith(prefix))
                  .forEach(key -> properties.put(key.substring(prefix.length()), System.getProperty(key)));

        }

        @Override
        public String getPid() {
            return pid;
        }

        @Override
        public Dictionary<String, Object> getProperties() {
            return properties;
        }

        @Override
        public void update(final Dictionary<String, ?> properties) {
            this.properties.clear();
            list(properties.keys()).forEach(key -> this.properties.put(key, properties.get(key)));
            update();
        }

        @Override
        public void delete() {
            // no-op
        }

        @Override
        public String getFactoryPid() {
            return factoryPid;
        }

        @Override
        public void update() {
            this.changeCount.incrementAndGet();
        }

        @Override
        public void setBundleLocation(final String location) {
            this.location = location;
        }

        @Override
        public String getBundleLocation() {
            return location;
        }

        @Override
        public long getChangeCount() {
            return changeCount.get();
        }
    }
}
