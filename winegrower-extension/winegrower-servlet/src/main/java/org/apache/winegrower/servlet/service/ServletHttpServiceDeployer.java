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
package org.apache.winegrower.servlet.service;

import static java.util.Arrays.asList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.EventListener;
import java.util.Hashtable;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.scanner.manifest.ManifestContributor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class ServletHttpServiceDeployer implements ServletContainerInitializer {

    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext servletContext) {
        if (is(servletContext, "winegrower.servlet.ripener.skip", false)) {
            return;
        }
        final Ripener.Configuration configuration = createConfiguration(servletContext);
        final Ripener.Impl ripener = new Ripener.Impl(configuration);
        if (is(servletContext, "winegrower.servlet.services.http.register", true)) {
            final ServletHttpService service = new ServletHttpService(servletContext);
            ripener.registerBuiltInService(HttpService.class, service, new Hashtable<>());
            final BundleContext rootBundle = ripener.getRegistry().getBundles().get(0L).getBundle().getBundleContext();
            Stream.of(Servlet.class, HttpServlet.class).forEach(base -> registerServletTracker(service, rootBundle, base));
            registerFilterTracker(service, rootBundle);
            Stream.of(
                    ServletContextListener.class, ServletContextAttributeListener.class,
                    HttpSessionListener.class, HttpSessionAttributeListener.class,
                    HttpSessionActivationListener.class, HttpSessionBindingListener.class,
                    HttpSessionIdListener.class,
                    ServletRequestListener.class, ServletRequestAttributeListener.class)
                  .forEach(base -> registerListenerTracker(service, rootBundle, base));
        }
        servletContext.setAttribute(Ripener.class.getName(), ripener.start());
        servletContext.addListener(new ServletContextListener() {

            @Override
            public void contextDestroyed(final ServletContextEvent sce) {
                ofNullable(sce.getServletContext().getAttribute(Ripener.class.getName())).map(Ripener.class::cast)
                        .ifPresent(Ripener::stop);
            }
        });
    }

    private Boolean is(final ServletContext servletContext, final String name, final boolean def) {
        return ofNullable(servletContext.getInitParameter(name)).map(String::valueOf).map(Boolean::parseBoolean).orElse(def);
    }

    private <T extends EventListener> void registerListenerTracker(final ServletHttpService service, final BundleContext bundleContext,
                                                                   final Class<T> type) {
        new StartupOnlyTracker<>(bundleContext, type, (reference, listener) -> {
            final Hashtable<String, Object> props = Stream.of(reference.getPropertyKeys()).map(String::valueOf)
                    .collect(Collector.of(Hashtable::new, (out, key) -> out.put(key, reference.getProperty(key)), (a, b) -> {
                        a.putAll(b);
                        return a;
                    }));
            ofNullable(listener.getClass().getAnnotation(WebListener.class))
                    .ifPresent(annotConfig -> of(annotConfig.value())
                            .filter(it -> !it.isEmpty())
                            .ifPresent(value -> props.put("description", value)));
            service.registerListener(listener, props);
        }).open();
    }

    private void registerFilterTracker(final ServletHttpService service, final BundleContext bundleContext) {
        new StartupOnlyTracker<>(bundleContext, Filter.class, (reference, filter) -> {
            final Hashtable<String, Object> props = Stream.of(reference.getPropertyKeys()).map(String::valueOf)
                    .collect(Collector.of(Hashtable::new, (out, key) -> out.put(key, reference.getProperty(key)), (a, b) -> {
                        a.putAll(b);
                        return a;
                    }));
            ofNullable(filter.getClass().getAnnotation(WebFilter.class)).ifPresent(annotConfig -> {
                of(annotConfig.asyncSupported()).filter(it -> it).ifPresent(value -> props.put("async-supported", value));
                of(annotConfig.displayName()).filter(it -> !it.isEmpty()).ifPresent(value -> props.put("display-name", value));
            });
            findMapping(reference, filter, WebFilter.class, WebFilter::urlPatterns)
                    .forEach(mapping -> service.registerFilter(mapping, filter, props));
        }).open();
    }

    private <T extends Servlet> void registerServletTracker(final ServletHttpService service, final BundleContext bundleContext,
            final Class<T> base) {
        new StartupOnlyTracker<>(bundleContext, base, (reference, servlet) -> {
            final Hashtable<String, Object> props = toProps(reference);
            ofNullable(servlet.getClass().getAnnotation(WebServlet.class)).ifPresent(annotConfig -> {
                props.put("servlet-name",
                        of(annotConfig.name()).filter(it -> !it.isEmpty()).orElse(servlet.getClass().getName()));
                of(annotConfig.loadOnStartup()).filter(it -> it >= 0).ifPresent(value -> props.put("load-on-startup", value));
                of(annotConfig.asyncSupported()).filter(it -> it).ifPresent(value -> props.put("async-supported", value));
                of(annotConfig.displayName()).filter(it -> !it.isEmpty()).ifPresent(value -> props.put("display-name", value));
            });
            findMapping(reference, servlet, WebServlet.class, WebServlet::urlPatterns)
                    .forEach(mapping -> service.registerServlet(mapping, servlet, props, null));
        }).open();
    }

    private <T, A extends Annotation> Stream<String> findMapping(final ServiceReference<T> reference, final T servlet,
            final Class<A> holder, final Function<A, String[]> mappingExtractor) {
        return ofNullable(servlet.getClass().getAnnotation(holder)).map(mappingExtractor).filter(it -> it.length > 0)
                .map(Stream::of).orElseGet(() -> ofNullable(reference.getProperty("alias")).map(String::valueOf).map(Stream::of)
                        .orElseGet(Stream::empty));
    }

    private Hashtable<String, Object> toProps(final ServiceReference<?> reference) {
        return Stream.of(reference.getPropertyKeys()).map(String::valueOf)
                .collect(Collector.of(Hashtable::new, (out, key) -> out.put(key, reference.getProperty(key)), (a, b) -> {
                    a.putAll(b);
                    return a;
                }));
    }

    private Ripener.Configuration createConfiguration(final ServletContext servletContext) {
        final Ripener.Configuration configuration = new Ripener.Configuration();
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.workdir")).map(String::valueOf)
                .map(File::new).ifPresent(configuration::setWorkDir);
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.prioritizedBundles"))
                .map(String::valueOf).filter(it -> !it.isEmpty()).map(it -> asList(it.split(",")))
                .ifPresent(configuration::setPrioritizedBundles);
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.ignoredBundles"))
                .map(String::valueOf).filter(it -> !it.isEmpty()).map(it -> asList(it.split(",")))
                .ifPresent(configuration::setIgnoredBundles);
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.scanningIncludes"))
                .map(String::valueOf).filter(it -> !it.isEmpty()).map(it -> asList(it.split(",")))
                .ifPresent(configuration::setScanningIncludes);
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.scanningExcludes"))
                .map(String::valueOf).filter(it -> !it.isEmpty()).map(it -> asList(it.split(",")))
                .ifPresent(configuration::setScanningExcludes);
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.manifestContributors"))
                .map(String::valueOf).filter(it -> !it.isEmpty()).map(it -> asList(it.split(","))).ifPresent(contributors -> {
                    configuration.setManifestContributors(contributors.stream().map(clazz -> {
                        try {
                            return Thread.currentThread().getContextClassLoader().loadClass(clazz).getConstructor().newInstance();
                        } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException
                                | ClassNotFoundException e) {
                            throw new IllegalArgumentException(e);
                        } catch (final InvocationTargetException e) {
                            throw new IllegalArgumentException(e.getTargetException());
                        }
                    }).map(ManifestContributor.class::cast).collect(toList()));
                });
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.jarFilter")).map(String::valueOf)
                .filter(it -> !it.isEmpty()).ifPresent(filter -> {
                    try {
                        configuration.setJarFilter((Predicate<String>) Thread.currentThread().getContextClassLoader()
                                .loadClass(filter).getConstructor().newInstance());
                    } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException
                            | ClassNotFoundException e) {
                        throw new IllegalArgumentException(e);
                    } catch (final InvocationTargetException e) {
                        throw new IllegalArgumentException(e.getTargetException());
                    }
                });
        return configuration;
    }

    private static class StartupOnlyTracker<A> extends ServiceTracker<A, A> {

        private StartupOnlyTracker(final BundleContext context,
                                   final Class<A> type,
                                   final BiConsumer<ServiceReference<A>, A> onService) {
            super(context, type, new ServiceTrackerCustomizer<A, A>() {

                @Override
                public A addingService(final ServiceReference<A> reference) {
                    final A service = context.getService(reference);
                    onService.accept(reference, service);
                    return service;
                }

                @Override
                public void modifiedService(final ServiceReference<A> reference, final A service) {
                    // no-op
                }

                @Override
                public void removedService(final ServiceReference<A> reference, final A service) {
                    // no-op
                }
            });
        }
    }
}
