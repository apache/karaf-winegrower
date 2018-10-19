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
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Stream;

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

        try {
            doStart(agentArgs);
        } catch (final Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private static void doStart(final String agentArgs) throws Throwable {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final Class<?> ripenerImplClass = loader.loadClass("org.apache.winegrower.Ripener$Impl");
        final Class<?> ripenerConfigurationClass = loader.loadClass("org.apache.winegrower.Ripener$Configuration");

        final Object ripener = ripenerImplClass.getConstructor(ripenerConfigurationClass)
                                               .newInstance(createConfiguration(ripenerConfigurationClass, agentArgs));
        doCall(ripener, "start", new Class<?>[0], new Object[0]);
        System.setProperty("wingrower.agent.started", "true");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                doCall(ripener, "stop", new Class<?>[0], new Object[0]);
            } catch (final Throwable throwable) {
                if (DEBUG) {
                    throwable.printStackTrace();
                } // else: not that important
            }
        }, WinegrowerAgent.class.getName() + "-shutdown"));
    }

    private static void doCall(final Object instance, final String mtd, final Class<?>[] paramTypes, final Object[] args) {
        try {
            instance.getClass().getMethod(mtd, paramTypes).invoke(instance, args);
        } catch (final InvocationTargetException ite) {
            final Throwable targetException = ite.getTargetException();
            if (RuntimeException.class.isInstance(targetException)) {
                throw RuntimeException.class.cast(targetException);
            }
            throw new IllegalStateException(targetException);
        } catch (final Exception ex) {
            if (RuntimeException.class.isInstance(ex)) {
                throw RuntimeException.class.cast(ex);
            }
            throw new IllegalStateException(ex);
        }
    }

    private static Object createConfiguration(final Class<?> configType, String agentArgs) throws Throwable {
        final Object configuration = configType.getConstructor().newInstance();
        ofNullable(extractConfig(agentArgs,"workDir="))
                .map(String::valueOf)
                .map(File::new)
                .ifPresent(value -> doCall(configuration, "setWorkDir", new Class<?>[]{File.class}, new Object[]{value}));
        ofNullable(extractConfig(agentArgs,"prioritizedBundles="))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(value -> doCall(configuration, "setPrioritizedBundles", new Class<?>[]{List.class}, new Object[]{value}));
        ofNullable(extractConfig(agentArgs,"ignoredBundles="))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(value -> doCall(configuration, "setIgnoredBundles", new Class<?>[]{Collection.class}, new Object[]{value}));
        ofNullable(extractConfig(agentArgs,"scanningIncludes="))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(value -> doCall(configuration, "setScanningIncludes", new Class<?>[]{Collection.class}, new Object[]{value}));
        ofNullable(extractConfig(agentArgs,"scanningExcludes="))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(value -> doCall(configuration, "setScanningExcludes", new Class<?>[]{Collection.class}, new Object[]{value}));
        ofNullable(extractConfig(agentArgs,"manifestContributors="))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(contributors -> {
                    try {
                        final Class<?> type = Thread.currentThread().getContextClassLoader().loadClass(
                                    "org.apache.winegrower.scanner.manifest.ManifestContributor");
                        final Collection<?> value = contributors.stream()
                                                      .map(clazz -> {
                                                          try {
                                                              return Thread.currentThread()
                                                                           .getContextClassLoader()
                                                                           .loadClass(clazz)
                                                                           .getConstructor()
                                                                           .newInstance();
                                                          } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
                                                              throw new IllegalArgumentException(e);
                                                          } catch (final InvocationTargetException e) {
                                                              throw new IllegalArgumentException(
                                                                      e.getTargetException());
                                                          }
                                                      })
                                                      .map(type::cast)
                                                      .collect(toList());
                        doCall(configuration, "setManifestContributors", new Class<?>[]{Collection.class}, new Object[]{value});
                    } catch (final ClassNotFoundException e) {
                        throw new IllegalArgumentException(e);
                    }
                });
        ofNullable(extractConfig(agentArgs,"jarFilter="))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .ifPresent(filter -> {
                    try {
                        final Predicate<String> predicate = (Predicate<String>) Thread.currentThread()
                              .getContextClassLoader().loadClass(filter).getConstructor().newInstance();
                        doCall(configuration, "setJarFilter", new Class<?>[]{Predicate.class}, new Object[]{predicate});
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
