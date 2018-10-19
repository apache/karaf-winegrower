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
package org.apache.winegrower.extension.agent;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.scanner.manifest.ManifestContributor;

public class WinegrowerAgent {
    private static final boolean DEBUG = Boolean.getBoolean("winegrower.agent.debug");

    public static void premain(final String agentArgs, final Instrumentation instrumentation) {
        agentmain(agentArgs, instrumentation);
    }

    public static void agentmain(final String agentArgs, final Instrumentation instrumentation) {
        if (Boolean.getBoolean("wingrower.agent.started")) {
            return;
        }
        if (DEBUG) {
            print("agentargs: " + agentArgs);
        }
        ofNullable(extractConfig(agentArgs, "libs="))
            .ifPresent(value -> Stream.of(value.split(","))
                .map(File::new)
                .filter(File::exists)
                .flatMap(it -> it.isDirectory() ?
                        ofNullable(it.listFiles()).map(Stream::of).orElseGet(Stream::empty) :
                        Stream.of(it))
                .filter(it -> it.getName().endsWith(".zip") || it.getName().endsWith(".jar"))
                .forEach(lib -> {
                    try {
                        instrumentation.appendToSystemClassLoaderSearch(new JarFile(lib));
                    } catch (final IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }));

        final Ripener ripener = new Ripener.Impl(createConfiguration(agentArgs));
        ripener.start();
        System.setProperty("wingrower.agent.started", "true");
        Runtime.getRuntime().addShutdownHook(new Thread(ripener::stop, WinegrowerAgent.class.getName() + "-shutdown"));
    }

    private static Ripener.Configuration createConfiguration(String agentArgs) {
        final Ripener.Configuration configuration = new Ripener.Configuration();
        ofNullable(extractConfig(agentArgs,"workDir="))
                .map(String::valueOf)
                .map(File::new)
                .ifPresent(configuration::setWorkDir);
        ofNullable(extractConfig(agentArgs,"prioritizedBundles="))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setPrioritizedBundles);
        ofNullable(extractConfig(agentArgs,"ignoredBundles="))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setIgnoredBundles);
        ofNullable(extractConfig(agentArgs,"scanningIncludes="))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setScanningIncludes);
        ofNullable(extractConfig(agentArgs,"scanningExcludes="))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setScanningExcludes);
        ofNullable(extractConfig(agentArgs,"manifestContributors="))
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
        ofNullable(extractConfig(agentArgs,"jarFilter="))
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

    private static String extractConfig(final String agentArgs, final String startStr) {
        if (agentArgs != null && agentArgs.contains(startStr)) {
            final int start = agentArgs.indexOf(startStr) + startStr.length();
            final int separator = agentArgs.indexOf('|', start);
            final int endIdx;
            if (separator > 0) {
                endIdx = separator;
            } else {
                endIdx = agentArgs.length();
            }
            final String value = agentArgs.substring(start, endIdx);
            if (DEBUG) {
                print(startStr + value);
            }
            return value;
        }
        if (DEBUG) {
            print("No configuration for " + startStr.substring(0, startStr.length() - 1));
        }
        return null;
    }

    private static void print(final String string) {
        System.out.println("[winegrower-agent:debug] " + string);
    }
}
