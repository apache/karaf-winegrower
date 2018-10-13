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

import org.apache.winegrower.Ripener;
import org.apache.winegrower.service.OSGiServices;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

public abstract class BaseInjection implements TestInstancePostProcessor, ParameterResolver {
    @Override
    public void postProcessTestInstance(final Object o, final ExtensionContext context) {
        ofNullable(store(context).get(Ripener.class, Ripener.class))
                .ifPresent(fwk -> fwk.getServices().inject(o));
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext context)
            throws ParameterResolutionException {
        final Class<?> type = parameterContext.getParameter().getType();
        return type == Ripener.class || type == OSGiServices.class || store(context).get(Ripener.class, Ripener.class).getServices().findService(type).isPresent();
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext context)
            throws ParameterResolutionException {
        final Class<?> type = parameterContext.getParameter().getType();
        if (type == Ripener.class) {
            return store(context).get(Ripener.class, Ripener.class);
        }
        if (type == OSGiServices.class) {
            return ofNullable(store(context).get(Ripener.class, Ripener.class))
                    .map(Ripener::getServices)
                    .orElse(null);
        }
        return store(context).get(Ripener.class, Ripener.class).getServices().findService(type).orElse(null);
    }

    protected abstract ExtensionContext.Store store(final ExtensionContext context);
}
