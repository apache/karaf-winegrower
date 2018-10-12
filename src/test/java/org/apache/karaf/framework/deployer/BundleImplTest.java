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
package org.apache.karaf.framework.deployer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.jar.Manifest;

import org.apache.karaf.framework.ContextualFramework;
import org.apache.karaf.framework.service.BundleRegistry;
import org.apache.karaf.framework.service.OSGiServices;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

class BundleImplTest {
    private static BundleImpl bundle;

    @BeforeAll
    static void initBundle() throws IOException {
        final BundleRegistry registry = new BundleRegistry();
        final Manifest manifest = new Manifest(new ByteArrayInputStream(("Manifest-Version: 1.0\nBundle-Version: 1.0\nBundle-SymbolicName: test\n").getBytes(StandardCharsets.UTF_8)));
        final OSGiServices services = new OSGiServices();
        final BundleContextImpl context = new BundleContextImpl(manifest, services, () -> bundle, registry);
        final File file = new File("target/missin");
        final ContextualFramework.Configuration configuration = new ContextualFramework.Configuration();
        bundle = new BundleImpl(manifest, file, context, configuration);
        registry.getBundles().put(bundle.getBundleId(), new OSGiBundleLifecycle(manifest, file, services, registry, configuration));
    }

    @Test
    void hasId() {
        assertTrue(bundle.getBundleId() > 0);
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
        assertNotNull(bundle.loadClass("org.apache.karaf.framework.test.simpleservice.MyServiceImpl"));
    }
}
