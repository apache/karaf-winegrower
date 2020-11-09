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

import org.apache.winegrower.lang.Substitutor;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Arrays.asList;
import static java.util.Collections.list;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public abstract class DefaultConfigurationAdmin implements ConfigurationAdmin {

    private final static String WINEGROWER_CONFIG_PATH = "winegrower.config.path";

    private final static String WINEGROWER_CONFIG_EXTENSION = ".cfg";

    private final Map<String, String> providedConfiguration;

    private final Map<Key, Configuration> configurations = new HashMap<>();

    private final Collection<ConfigurationListener> configurationListeners;

    public DefaultConfigurationAdmin(final Map<String, String> providedConfiguration,
                                     final Collection<ConfigurationListener> configurationListeners) {
        this.providedConfiguration = providedConfiguration;
        this.configurationListeners = configurationListeners;
    }

    public void preload(final List<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        names.forEach(it -> getConfiguration(it).setBundleLocation(null));
    }

    public Map<String, String> getProvidedConfiguration() {
        return providedConfiguration;
    }

    @Override
    public Configuration getFactoryConfiguration(final String factoryPid, final String name, final String location) {
        return getOrCreate(factoryPid, null, location, name);
    }

    @Override
    public Configuration getFactoryConfiguration(final String factoryPid, final String name) {
        return getOrCreate(factoryPid, null, null, name);
    }

    @Override
    public Configuration createFactoryConfiguration(final String pid) {
        return createFactoryConfiguration(pid, null);
    }

    @Override
    public Configuration createFactoryConfiguration(final String pid, final String location) {
        return getOrCreate(pid, null, location, null);
    }

    @Override
    public Configuration getConfiguration(final String pid, final String location) {
        return getOrCreate(null, pid, location, null);
    }

    @Override
    public Configuration getConfiguration(final String pid) {
        return getConfiguration(pid, null);
    }

    @Override
    public Configuration[] listConfigurations(final String filter) {
        try {
            final Filter predicate = filter == null ? null : FrameworkUtil.createFilter(filter);
            return configurations.values().stream().filter(it -> predicate == null || predicate.match(it.getProperties()))
                    .toArray(Configuration[]::new);
        } catch (final InvalidSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Configuration getOrCreate(final String factoryPid, final String pid, final String location,
                                      final String name) {
        final Key key = new Key(factoryPid, pid);
        final Configuration existing = configurations.get(key);
        if (existing != null) {
            return existing;
        }
        final DefaultConfiguration created = new DefaultConfiguration(providedConfiguration,
                factoryPid, pid, location, name) {
            @Override
            public void setBundleLocation(final String location) {
                super.setBundleLocation(location);
                final ConfigurationEvent event = new ConfigurationEvent(
                        getSelfReference(), ConfigurationEvent.CM_LOCATION_CHANGED, factoryPid, pid);
                configurationListeners.forEach(it -> it.configurationEvent(event));
            }

            @Override
            public void update(Dictionary<String, ?> properties) {
                super.update(properties);
                final ConfigurationEvent event = new ConfigurationEvent(
                        getSelfReference(), ConfigurationEvent.CM_UPDATED, factoryPid, pid);
                configurationListeners.forEach(it -> it.configurationEvent(event));
            }

            @Override
            public void delete() {
                super.delete();
                final ConfigurationEvent event = new ConfigurationEvent(
                        getSelfReference(), ConfigurationEvent.CM_DELETED, factoryPid, pid);
                configurationListeners.forEach(it -> it.configurationEvent(event));
            }
        };
        configurations.putIfAbsent(key, created);
        return created;
    }

    protected abstract ServiceReference<ConfigurationAdmin> getSelfReference();

    private static class DefaultConfiguration implements Configuration {

        private final String factoryPid;

        private final String pid;

        private final Map<String, String> defaultConfig = new HashMap<>();

        private final File defaultExternalConfigLocation;

        private final Map<String, String> configRegistry;

        private final String name;

        private String location;

        private final Hashtable<String, Object> properties;

        private final AtomicLong changeCount = new AtomicLong();

        private final Set<ConfigurationAttribute> attributes = new HashSet<>();

        private DefaultConfiguration(final Map<String, String> configRegistry, final String factoryPid, final String pid,
                                     final String location, final String name) {
            this.configRegistry = configRegistry;
            this.factoryPid = factoryPid;
            this.pid = pid;
            this.location = location;
            this.name = name;
            this.properties = new Hashtable<>();
            this.defaultExternalConfigLocation = new File(
                    // support a cascade of known "homes"
                    System.getProperty(WINEGROWER_CONFIG_PATH,
                            System.getProperty("karaf.base",
                                    System.getProperty("catalina.base",
                                            System.getProperty("karaf.home", System.getProperty("karaf.etc"))))),
                    pid + WINEGROWER_CONFIG_EXTENSION);
            loadConfig(pid);

        }

        private void loadConfig(final String pid) {
            final String prefix = "winegrower.service." + pid + "."; // for "global" registries like system props

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

            // then the default registry which is considered "in JVM" so less prioritized than external config
            configRegistry.entrySet().stream().filter(it -> it.getKey().startsWith(prefix))
                    .forEach(entry -> properties.put(entry.getKey().substring(prefix.length()), entry.getValue()));

            // then from an external file
            if (defaultExternalConfigLocation.isFile()) {
                try (final InputStream stream = new FileInputStream(defaultExternalConfigLocation)) {
                    this.properties.putAll(load(stream));
                } catch (final IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }

            final Map<String, String> placeholders = new HashMap<>(Map.class.cast(properties));
            placeholders.putAll(Map.class.cast(System.getProperties()));
            placeholders.putAll(System.getenv());
            final Substitutor substitutor = new Substitutor(placeholders);

            // and finally from system properties and env variables
            // (env is for the machine so less precise than system props so set first)
            final String envPrefix = prefix.toUpperCase(ROOT).replace('.', '_');
            System.getenv().keySet().stream()
                    .filter(it -> it.length() > prefix.length() && envPrefix.equalsIgnoreCase(it.substring(0, envPrefix.length())))
                    .forEach(key -> {
                        final String k = key.substring(envPrefix.length());
                        // env keys loose the case so in case it is important, enable to force the key name
                        // ex: to set configuration{pid=a.b.c, key=fooBar, value=dummy} you would set:
                        //     A_B_C_FOOBAR_NAME=fooBar
                        //     A_B_C_FOOBAR=dummy
                        // note that the FOOBAR in the key is not important, previous config is the same than:
                        //     A_B_C_1_NAME=fooBar
                        //     A_B_C_1=dummy
                        // but when there key is not ambiguous (all lowercase) it is simpler to set (key=foobar):
                        //     A_B_C_FOOBAR=dummy
                        final String value = System.getenv(key);
                        properties.put(
                                ofNullable(System.getenv(key + "_NAME")).orElseGet(() -> k.toLowerCase(ROOT)),
                                value.contains("${") && value.contains("}") ? substitutor.replace(value) : value);
                    });

            System.getProperties().stringPropertyNames().stream()
                    .filter(it -> it.startsWith(prefix))
                    .forEach(key -> {
                        final String value = System.getProperty(key);
                        properties.put(
                                key.substring(prefix.length()),
                                value.contains("${") && value.contains("}") ? substitutor.replace(value) : value);
                    });

            // ensure the factoryPid/pid is there if exists
            ofNullable(pid).ifPresent(v -> properties.putIfAbsent("service.pid", v));
            ofNullable(factoryPid).ifPresent(v -> properties.putIfAbsent("service.factoryPid", v));
            ofNullable(name).ifPresent(v -> properties.putIfAbsent("name", v));
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
        public Dictionary<String, Object> getProcessedProperties(final ServiceReference<?> reference) {
            return reference.getProperties();
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
        public boolean updateIfDifferent(final Dictionary<String, ?> properties) {
            if (properties == null || list(properties.keys()).stream()
                    .anyMatch(it -> !properties.get(it).equals(this.properties.get(it)))) {
                update(properties);
                return true;
            }
            return false;
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

        @Override
        public void addAttributes(final ConfigurationAttribute... attrs) {
            ofNullable(attrs).ifPresent(values -> attributes.addAll(asList(attrs)));
        }

        @Override
        public Set<ConfigurationAttribute> getAttributes() {
            return attributes;
        }

        @Override
        public void removeAttributes(ConfigurationAttribute... attrs) throws IOException {
            ofNullable(attrs).ifPresent(values -> attributes.removeAll(asList(attrs)));
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
            final Map<String, String> placeholders = new HashMap<>(Map.class.cast(properties));
            placeholders.putAll(Map.class.cast(System.getProperties()));
            placeholders.putAll(System.getenv());
            final Substitutor substitutor = new Substitutor(placeholders);
            return properties.stringPropertyNames().stream().collect(toMap(identity(),
                    it -> {
                        final String value = properties.getProperty(it);
                        return value.contains("${") && value.contains("}") ? substitutor.replace(value) : value;
                    }));
        }
    }

    private static class Key {

        private final String factoryPid;

        private final String pid;

        private final int hash;

        private Key(final String factoryPid, final String pid) {
            this.factoryPid = factoryPid;
            this.pid = pid;
            this.hash = Objects.hash(factoryPid, pid);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Key key = Key.class.cast(o);
            return Objects.equals(factoryPid, key.factoryPid) && Objects.equals(pid, key.pid);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return "Key{factoryPid='" + factoryPid + "', pid='" + pid + "'}";
        }
    }
}
