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
package org.apache.winegrower.extension.testing.junit5;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Predicate;

import org.apache.winegrower.api.LifecycleCallbacks;
import org.apache.winegrower.extension.testing.junit5.internal.WinegrowerExtension;
import org.apache.winegrower.extension.testing.junit5.internal.engine.CaptureExtensionRegistry;
import org.apache.winegrower.scanner.manifest.ManifestContributor;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Ensure the class is executed under a winegrower context.
 */
@Target(TYPE)
@Retention(RUNTIME)
@ExtendWith(CaptureExtensionRegistry.class)
@ExtendWith(WinegrowerExtension.class)
public @interface Winegrower {
    String workDir() default "";
    String[] prioritizedBundles() default {};
    Class<? extends ManifestContributor>[] manifestContributor() default {};
    Class<? extends JarFilter> jarFilter() default JarFilter.class;
    String[] ignoredBundles() default {};
    String[] scanningExcludes() default {};
    String[] scanningIncludes() default {};
    Class<? extends LifecycleCallbacks>[] lifecycleCallbacks() default {};
    boolean useLifecycleCallbacks() default true;

    interface JarFilter extends Predicate<String> {
    }
}
