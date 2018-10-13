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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BundleActivatorHandler implements ServiceLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(BundleActivatorHandler.class);

    private final BundleActivator activator;
    private final BundleContext context;

    public BundleActivatorHandler(final BundleActivator activator, final BundleContext context) {
        this.activator = activator;
        this.context = context;
    }

    public BundleActivator getActivator() {
        return activator;
    }

    @Override
    public void start() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting activator {}", activator);
        }
        try {
            activator.start(context);
        } catch (final Exception e) {
            if (RuntimeException.class.isInstance(e)) {
                throw RuntimeException.class.cast(e);
            }
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void stop() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Stopping activator {}", activator);
        }
        try {
            activator.stop(context);
        } catch (final Exception e) {
            if (RuntimeException.class.isInstance(e)) {
                throw RuntimeException.class.cast(e);
            }
            throw new IllegalStateException(e);
        }
    }
}
