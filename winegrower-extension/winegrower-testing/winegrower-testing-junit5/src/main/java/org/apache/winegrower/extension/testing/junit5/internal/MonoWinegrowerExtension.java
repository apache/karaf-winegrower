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
package org.apache.winegrower.extension.testing.junit5.internal;

import static java.util.Optional.ofNullable;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.winegrower.ContextualFramework;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MonoWinegrowerExtension extends BaseInjection implements BeforeAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
            .create(MonoWinegrowerExtension.class.getName());

    private static final AtomicReference<Instance> INSTANCE = new AtomicReference<>();

    @Override
    public void beforeAll(final ExtensionContext extensionContext) throws Exception {
        Instance instance = INSTANCE.get();
        if (instance == null) {
            synchronized (INSTANCE) {
                instance = INSTANCE.get();
                if (instance == null) {
                    final Iterator<ContextualFramework.Configuration> configurations = ServiceLoader
                            .load(ContextualFramework.Configuration.class).iterator();
                    final ContextualFramework.Configuration configuration = configurations.hasNext() ? configurations.next()
                            : new ContextualFramework.Configuration();
                    instance = new Instance(new ContextualFramework.Impl(configuration));
                    Runtime.getRuntime().addShutdownHook(new Thread() {

                        {
                            setName(getClass().getName() + "-shutdown-hook");
                        }

                        @Override
                        public void run() {
                            ofNullable(INSTANCE.get()).ifPresent(Instance::close);
                        }
                    });
                    instance.framework.start();
                }
            }
        }
        store(extensionContext).put(ContextualFramework.class, instance.framework);
    }

    @Override
    protected ExtensionContext.Store store(final ExtensionContext context) {
        return context.getStore(NAMESPACE);
    }

    private static class Instance implements AutoCloseable {

        private final ContextualFramework framework;

        private Instance(final ContextualFramework framework) {
            this.framework = framework;
        }

        @Override
        public void close() {
            framework.stop();
        }
    }
}
