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

import static java.util.Arrays.asList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.extension.testing.junit5.Winegrower;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class WinegrowerExtension extends BaseInjection implements BeforeAllCallback, AfterAllCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(WinegrowerExtension.class.getName());

    @Override
    public void beforeAll(final ExtensionContext context) {
        final Ripener.Configuration configuration = context.getElement()
                                                                       .map(e -> e.getAnnotation(Winegrower.class))
                                                                       .map(this::createConfiguration)
                                                                       .orElseGet(Ripener.Configuration::new);
        store(context).put(Ripener.class, new Ripener.Impl(configuration).start());
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        ofNullable(store(context).get(Ripener.class, Ripener.class))
                .ifPresent(Ripener::stop);
    }

    private Ripener.Configuration createConfiguration(final Winegrower winegrower) {
        final Ripener.Configuration configuration = new Ripener.Configuration();
        of(winegrower.workDir())
                .filter(it -> !it.isEmpty())
                .ifPresent(wd -> configuration.setWorkDir(new File(wd)));
        of(winegrower.prioritizedBundles())
                .filter(it -> it.length > 0)
                .ifPresent(prioritizedBundles -> configuration.setScanningIncludes(asList(prioritizedBundles)));
        of(winegrower.scanningIncludes())
                .filter(it -> it.length > 0)
                .ifPresent(includes -> configuration.setScanningIncludes(asList(includes)));
        of(winegrower.ignoredBundles())
                .filter(it -> it.length > 0)
                .ifPresent(bundles -> configuration.setIgnoredBundles(asList(bundles)));
        of(winegrower.scanningExcludes())
                .filter(it -> it.length > 0)
                .ifPresent(excludes -> configuration.setScanningExcludes(asList(excludes)));
        of(winegrower.jarFilter())
                .filter(it -> it != Winegrower.JarFilter.class)
                .ifPresent(filter -> {
                    try {
                        configuration.setJarFilter(filter.getConstructor().newInstance());
                    } catch (final InstantiationException | IllegalAccessException | NoSuchMethodException e) {
                        throw new IllegalArgumentException(e);
                    } catch (final InvocationTargetException e) {
                        throw new IllegalArgumentException(e.getTargetException());
                    }
                });
        of(winegrower.manifestContributor())
                .filter(it -> it.length > 0)
                .ifPresent(contributors -> configuration.setManifestContributors(Stream.of(contributors).map(it -> {
                    try {
                        return it.getConstructor().newInstance();
                    } catch (final InstantiationException | IllegalAccessException | NoSuchMethodException e) {
                        throw new IllegalArgumentException(e);
                    } catch (final InvocationTargetException e) {
                        throw new IllegalArgumentException(e.getTargetException());
                    }
                }).collect(toList())));
        return configuration;
    }

    @Override
    protected ExtensionContext.Store store(final ExtensionContext context) {
        return context.getStore(NAMESPACE);
    }
}
