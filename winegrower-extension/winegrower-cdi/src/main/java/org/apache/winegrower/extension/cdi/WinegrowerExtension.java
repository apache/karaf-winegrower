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
package org.apache.winegrower.extension.cdi;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Extension;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.service.OSGiServices;
import org.apache.winegrower.service.ServiceReferenceImpl;
import org.apache.winegrower.service.ServiceRegistrationImpl;
import org.osgi.framework.Constants;

public class WinegrowerExtension implements Extension {
    private static final ThreadLocal<Ripener> RIPENER_LOCATOR = new ThreadLocal<>();

    public interface RipenerLocator {
        static <T> T wrapCdiBoot(final Ripener ripener, final Supplier<T> initializer) {
            cdiWillStart(ripener);
            try {
                return initializer.get();
            } finally {
                cdiStarted();
            }
        }

        static void cdiStarted() {
            RIPENER_LOCATOR.remove();
        }

        static void cdiWillStart(Ripener ripener) {
            RIPENER_LOCATOR.set(ripener);
        }
    }

    void registerServices(@Observes final AfterBeanDiscovery discovery) {
        final Ripener ripener = RIPENER_LOCATOR.get();
        if (ripener == null) {
            throw new IllegalStateException("No Ripener provided, did you use wrapCdiBoot?");
        }
        ripener.getServices().getServices().stream()
                    .filter(it -> Constants.SCOPE_SINGLETON.equals(it.getReference().getProperty(Constants.SERVICE_SCOPE)))
                    .forEach(reg -> {
                        final Object reference = ServiceReferenceImpl.class.cast(reg.getReference()).getReference();
                        final Class<?>[] types = Stream.of(ServiceRegistrationImpl.class.cast(reg).getClasses())
                                                       .filter(it -> !it.startsWith("java.")) // ignore too common types
                                                       .map(this::load)
                                                       .filter(Objects::nonNull)
                                                       .toArray(Class<?>[]::new);
                        if (types.length > 0) {
                            discovery.addBean()
                                     .id("winegrower-service#" + reg.getReference().getProperty(Constants.SERVICE_ID))
                                     .scope(Dependent.class)
                                     .createWith(c -> reference)
                                     .beanClass(reference.getClass())
                                     .types(Stream.concat(Stream.of(types), Stream.of(Object.class)).toArray(Class<?>[]::new))
                                     .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE);
                        }

                    });

        // add global services
        discovery.addBean()
                 .id("winegrower#ripener")
                 .scope(ApplicationScoped.class)
                 .createWith(c -> ripener)
                 .beanClass(Ripener.class)
                 .types(Object.class, Ripener.class)
                 .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE);
        discovery.addBean()
                 .id("winegrower#services")
                 .scope(Dependent.class)
                 .createWith(c -> ripener.getServices())
                 .beanClass(OSGiServices.class)
                 .types(Object.class, OSGiServices.class)
                 .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE);
        discovery.addBean()
                 .id("winegrower#configuration")
                 .scope(Dependent.class)
                 .createWith(c -> ripener.getConfiguration())
                 .beanClass(Ripener.Configuration.class)
                 .types(Object.class, Ripener.Configuration.class)
                 .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE);
    }

    private Class<?> load(final String name) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(name);
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }
}
