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
package org.apache.winegrower.servlet;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Predicate;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.scanner.manifest.ManifestContributor;

@WebListener
public class RipenerLifecycle implements ServletContextListener {
    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        final ServletContext servletContext = sce.getServletContext();
        if (ofNullable(servletContext
                          .getInitParameter("winegrower.servlet.ripener.skip"))
            .map(String::valueOf)
            .map(Boolean::parseBoolean)
            .orElse(false)) {
            return;
        }
        final Ripener.Configuration configuration = createConfiguration(servletContext);
        servletContext
           .setAttribute(Ripener.class.getName(), new Ripener.Impl(configuration).start());
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        ofNullable(sce.getServletContext().getAttribute(Ripener.class.getName()))
                .map(Ripener.class::cast)
                .ifPresent(Ripener::stop);
    }

    private Ripener.Configuration createConfiguration(final ServletContext servletContext) {
        final Ripener.Configuration configuration = new Ripener.Configuration();
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.workdir"))
                .map(String::valueOf)
                .map(File::new)
                .ifPresent(configuration::setWorkDir);
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.prioritizedBundles"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setPrioritizedBundles);
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.ignoredBundles"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setIgnoredBundles);
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.scanningIncludes"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setScanningIncludes);
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.scanningExcludes"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setScanningExcludes);
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.manifestContributors"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(contributors -> {
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
        ofNullable(servletContext.getInitParameter("winegrower.servlet.ripener.configuration.jarFilter"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .ifPresent(filter -> {
                    try {
                        configuration.setJarFilter((Predicate<String>) Thread.currentThread().getContextClassLoader().loadClass(filter)
                                                                             .getConstructor().newInstance());
                    } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
                        throw new IllegalArgumentException(e);
                    } catch (final InvocationTargetException e) {
                        throw new IllegalArgumentException(e.getTargetException());
                    }
                });
        return configuration;
    }
}
