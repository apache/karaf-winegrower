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
package org.apache.karaf.framework;

import static java.util.Comparator.comparing;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.karaf.framework.deployer.OSGiBundleLifecycle;
import org.apache.karaf.framework.scanner.StandaloneScanner;
import org.apache.karaf.framework.service.BundleRegistry;
import org.apache.karaf.framework.service.OSGiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextualFramework implements AutoCloseable {

    private final static Logger LOGGER = LoggerFactory.getLogger(ContextualFramework.class);

    public final OSGiServices services = new OSGiServices();
    public final BundleRegistry registry = new BundleRegistry();

    public synchronized ContextualFramework start() {
        LOGGER.info("Starting Apache Karaf Single Framework");
        new StandaloneScanner()
                .findOSGiBundles()
                .stream()
                .sorted(comparing(b -> b.getJar().getName()))
                .map(it -> new OSGiBundleLifecycle(it.getManifest(), it.getJar(), services, registry))
                .peek(OSGiBundleLifecycle::start)
                .peek(it -> registry.getBundles().put(it.getBundle().getBundleId(), it))
                .forEach(bundle -> {
                    // todo: log
                });
        return this;
    }

    public synchronized void stop() {
        LOGGER.info("Stopping Apache Karaf Single Framework");
        final Map<Long, OSGiBundleLifecycle> bundles = registry.getBundles();
        bundles.forEach((k, v) -> v.stop());
        bundles.clear();
    }

    @Override // for try with resource syntax
    public void close() {
        stop();
    }

    public static void main(final String[] args) {
        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread() {

            {
                setName(getClass().getName() + "-shutdown-hook");
            }

            @Override
            public void run() {
                latch.countDown();
            }
        });
        try (final ContextualFramework framework = new ContextualFramework().start()) {
            try {
                latch.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
