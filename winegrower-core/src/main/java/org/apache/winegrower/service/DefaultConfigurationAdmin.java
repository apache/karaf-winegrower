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
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

public class DefaultConfigurationAdmin implements ConfigurationAdmin {

    private final static String WINEGROWER_CONFIG_PATH = "winegrower.config.path";
    private final static String WINEGROWER_CONFIG_EXTENSION = ".cfg";

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
        private final Map<String, String> defaultConfig = new HashMap<>();
        private final File defaultExternalConfigLocation;
        private String location;
        private final Hashtable<String, Object> properties;
        private final AtomicLong changeCount = new AtomicLong();

        private DefaultConfiguration(final String factoryPid, final String pid, final String location) {
            this.factoryPid = factoryPid;
            this.pid = pid;
            this.location = location;
            this.properties = new Hashtable<>();
            this.defaultExternalConfigLocation = new File(
                    // support a cascade of known "homes"
                    System.getProperty(WINEGROWER_CONFIG_PATH,
                            System.getProperty("karaf.base",
                                    System.getProperty("catalina.base",
                                            System.getProperty("karaf.home")))),
                    pid + WINEGROWER_CONFIG_EXTENSION);
            loadConfig(pid);

        }

        private void loadConfig(final String pid) {
            // we first read the config from the classpath (lowest priority)
            try (final InputStream embedConfig = Thread.currentThread().getContextClassLoader()
                                                       .getResourceAsStream(pid + WINEGROWER_CONFIG_EXTENSION)) {
                if (embedConfig != null) {
                    defaultConfig.putAll(load(embedConfig));
                }
            } catch (final IOException e) {
                throw new IllegalArgumentException(e);
            }
            properties.putAll(defaultConfig);

            // then from an external file
            if (defaultExternalConfigLocation.isFile()) {
                try (final InputStream stream = new FileInputStream(defaultExternalConfigLocation)) {
                    this.properties.putAll(load(stream));
                } catch (final IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }

            // and finally from system properties
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
            loadConfig(pid);
            this.properties.putAll(converter(properties));
            this.changeCount.incrementAndGet();
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
            update(new Hashtable<>());
            if (defaultExternalConfigLocation.isFile()) {
                final Properties output = new Properties();
                output.putAll(properties);
                try (final OutputStream outputStream = new FileOutputStream(defaultExternalConfigLocation)) {
                    output.store(outputStream, "Updated configuration on " + new Date());
                } catch (final IOException e) {
                    throw new IllegalArgumentException(e);
                }
            } // else: don't modify neither the classpath nor system properties - this would be insane even if doable
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

        private Map<String, String> converter(final Dictionary<String, ?> properties) {
            return list(properties.keys()).stream().collect(toMap(identity(), it -> properties.get(it).toString()));
        }

        private Map<String, String> load(final InputStream stream) {
            final Properties properties = new Properties();
            try {
                properties.load(stream);
            } catch (final IOException e) {
                throw new IllegalArgumentException(e);
            }
            return Map.class.cast(properties);
        }
    }
}
