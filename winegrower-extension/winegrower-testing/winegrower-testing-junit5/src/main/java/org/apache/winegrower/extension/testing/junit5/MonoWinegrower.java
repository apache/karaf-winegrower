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

import org.apache.winegrower.Ripener;
import org.apache.winegrower.extension.testing.junit5.internal.MonoWinegrowerExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Ensure the class is executed under a winegrower context.
 * Compared to {@link Winegrower}, it does start the container
 * only once for the whole JVM lifecycle and it uses a SPI
 * on the {@link Ripener.Configuration}
 * to initialize the container (to let it be shared accross tests).
 */
@Target(TYPE)
@Retention(RUNTIME)
@ExtendWith(MonoWinegrowerExtension.class)
public @interface MonoWinegrower {
}
