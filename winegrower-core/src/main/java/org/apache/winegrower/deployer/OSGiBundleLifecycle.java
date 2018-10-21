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
package org.apache.winegrower.deployer;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.jar.Manifest;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.service.BundleActivatorHandler;
import org.apache.winegrower.service.BundleRegistry;
import org.apache.winegrower.service.OSGiServices;
import org.osgi.framework.BundleActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OSGiBundleLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(OSGiBundleLifecycle.class);

    private final BundleContextImpl context;
    private final BundleImpl bundle;
    private BundleActivatorHandler activator;

    public OSGiBundleLifecycle(final Manifest manifest, final File file, final OSGiServices services,
                               final BundleRegistry registry, final Ripener.Configuration configuration,
                               final long id, final Collection<String> includedResources) {
        this.context = new BundleContextImpl(manifest, services, this::getBundle, registry);
        this.bundle = new BundleImpl(manifest, file, context, configuration, id, includedResources);
    }

    public BundleActivatorHandler getActivator() {
        return activator;
    }

    public BundleImpl getBundle() {
        return bundle;
    }

    public OSGiBundleLifecycle start() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting {}", bundle);
        }

        final String activatorClass = context.getManifest().getMainAttributes().getValue("Bundle-Activator");
        if (activatorClass != null) {
            try {
                activator = new BundleActivatorHandler(BundleActivator.class.cast(getBundle().getLoader()
                              .loadClass(activatorClass)
                              .getConstructor()
                              .newInstance()), context);
                activator.start();
            } catch (final NoClassDefFoundError | InstantiationException | IllegalAccessException |
                    NoSuchMethodException | ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            } catch (final InvocationTargetException e) {
                throw new IllegalArgumentException(e.getTargetException());
            }
        }

        bundle.onStart();

        return this;
    }

    public void stop() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Stopping {}", bundle);
        }
        if (activator != null) {
            activator.stop();
        }
        bundle.onStop();
    }

    @Override
    public String toString() {
        return "OSGiBundleLifecycle{bundle=" + bundle + '}';
    }
}
