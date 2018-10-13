/**
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 * <p>
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
package org.apache.winegrower.deployer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.jar.Manifest;

import org.apache.winegrower.ContextualFramework;
import org.apache.winegrower.service.BundleRegistry;
import org.apache.winegrower.service.OSGiServices;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

class BundleImplTest {
    private static BundleImpl bundle;

    @BeforeAll
    static void initBundle() throws IOException {
        final Manifest manifest = new Manifest(new ByteArrayInputStream(("Manifest-Version: 1.0\nBundle-Version: 1.0\nBundle-SymbolicName: test\n").getBytes(StandardCharsets.UTF_8)));
        final ContextualFramework.Configuration configuration = new ContextualFramework.Configuration();
        final OSGiServices services = new OSGiServices(new ContextualFramework.Impl(configuration));
        final BundleRegistry registry = new BundleRegistry(services, configuration);
        final BundleContextImpl context = new BundleContextImpl(manifest, services, () -> bundle, registry);
        final File file = new File(registry.getFramework().getParentFile(), "test-classes");
        bundle = new BundleImpl(manifest, file, context, configuration, 1);
        registry.getBundles().put(bundle.getBundleId(), new OSGiBundleLifecycle(manifest, file, services, registry, configuration, 1));
    }

    @Test
    void hasId() {
        assertEquals(1L, bundle.getBundleId());
    }

    @Test
    void getEntryPaths() {
        final Enumeration<String> entries = bundle.getEntryPaths("org/apache/winegrower/test/simpleservice/META-INF");
        assertTrue(entries.hasMoreElements());
        assertEquals("org/apache/winegrower/test/simpleservice/META-INF/MANIFEST.MF", entries.nextElement());
        assertFalse(entries.hasMoreElements());
    }

    @Test
    void findEntriesDirectNameNotRecursive() {
        final Enumeration<URL> entries = bundle.findEntries("org/apache/winegrower/test/simpleservice",
                "MyServiceImpl.class", false);
        assertTrue(entries.hasMoreElements());
        assertNotNull(entries.nextElement());
        assertFalse(entries.hasMoreElements());
    }

    @Test
    void findEntriesPatternRecursive() {
        final Enumeration<URL> entries = bundle.findEntries("org/apache/winegrower/test/simpleservice",
                "MyActivator.class", true);
        assertTrue(entries.hasMoreElements());
        assertNotNull(entries.nextElement());
        assertFalse(entries.hasMoreElements());
    }

    @Test
    void hasLoader() {
        assertNotNull(bundle.getLoader());
    }

    @Test
    void hasContext() {
        assertNotNull(bundle.getBundleContext());
    }

    @Test
    void version() {
        assertEquals("1.0.0", bundle.getVersion().toString());
    }

    @Test
    void state() {
        bundle.start();
        assertEquals(Bundle.ACTIVE, bundle.getState());
        bundle.stop();
        assertEquals(Bundle.UNINSTALLED, bundle.getState());
    }

    @Test
    void symbolicName() {
        assertEquals("test", bundle.getSymbolicName());
    }

    @Test
    void loadClass() throws ClassNotFoundException {
        assertNotNull(bundle.loadClass("org.apache.winegrower.test.simpleservice.MyServiceImpl"));
    }
}
