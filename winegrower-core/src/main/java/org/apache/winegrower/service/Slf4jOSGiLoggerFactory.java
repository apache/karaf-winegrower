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
package org.apache.winegrower.service;

import org.osgi.framework.Bundle;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerConsumer;
import org.osgi.service.log.LoggerFactory;

public class Slf4jOSGiLoggerFactory implements LoggerFactory {
    @Override
    public Logger getLogger(final String s) {
        return new Slf4jLogger(org.slf4j.LoggerFactory.getLogger(s));
    }

    @Override
    public Logger getLogger(final Class<?> aClass) {
        return getLogger(aClass.getName());
    }

    @Override
    public <L extends Logger> L getLogger(final String s, final Class<L> aClass) {
        return aClass.cast(getLogger(s));
    }

    @Override
    public <L extends Logger> L getLogger(final Class<?> aClass, final Class<L> aClass1) {
        return aClass1.cast(getLogger(aClass.getName()));
    }

    @Override
    public <L extends Logger> L getLogger(final Bundle bundle, final String s, final Class<L> aClass) {
        return aClass.cast(getLogger(s));
    }

    private static class Slf4jLogger implements FormatterLogger {
        private final org.slf4j.Logger delegate;

        private Slf4jLogger(final org.slf4j.Logger logger) {
            this.delegate = logger;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public boolean isTraceEnabled() {
            return delegate.isTraceEnabled();
        }

        @Override
        public void trace(final String message) {
            delegate.trace(message);
        }

        @Override
        public void trace(final String format, final Object arg) {
            delegate.trace(format, arg);
        }

        @Override
        public void trace(final String format, final Object arg1, final Object arg2) {
            delegate.trace(format, arg1, arg2);
        }

        @Override
        public void trace(final String format, final Object... arguments) {
            delegate.trace(format, arguments);
        }

        @Override
        public <E extends Exception> void trace(final LoggerConsumer<E> consumer) throws E {
            if (delegate.isTraceEnabled()) {
                consumer.accept(this);
            }
        }

        @Override
        public boolean isDebugEnabled() {
            return delegate.isDebugEnabled();
        }

        @Override
        public void debug(final String message) {
            delegate.debug(message);
        }

        @Override
        public void debug(final String format, final Object arg) {
            delegate.debug(format, arg);
        }

        @Override
        public void debug(final String format, final Object arg1, final Object arg2) {
            delegate.debug(format, arg1, arg2);
        }

        @Override
        public void debug(final String format, final Object... arguments) {
            delegate.debug(format, arguments);
        }

        @Override
        public <E extends Exception> void debug(final LoggerConsumer<E> consumer) throws E {
            if (isDebugEnabled()) {
                consumer.accept(this);
            }
        }

        @Override
        public boolean isInfoEnabled() {
            return delegate.isInfoEnabled();
        }

        @Override
        public void info(final String message) {
            delegate.info(message);
        }

        @Override
        public void info(final String format, final Object arg) {
            delegate.info(format, arg);
        }

        @Override
        public void info(final String format, final Object arg1, final Object arg2) {
            delegate.info(format, arg1, arg2);
        }

        @Override
        public void info(final String format, final Object... arguments) {
            delegate.info(format, arguments);
        }

        @Override
        public <E extends Exception> void info(final LoggerConsumer<E> consumer) throws E {
            if (isInfoEnabled()) {
                consumer.accept(this);
            }
        }

        @Override
        public boolean isWarnEnabled() {
            return delegate.isWarnEnabled();
        }

        @Override
        public void warn(final String message) {
            delegate.warn(message);
        }

        @Override
        public void warn(final String format, final Object arg) {
            delegate.warn(format, arg);
        }

        @Override
        public void warn(final String format, final Object arg1, final Object arg2) {
            delegate.warn(format, arg1, arg2);
        }

        @Override
        public void warn(final String format, final Object... arguments) {
            delegate.warn(format, arguments);
        }

        @Override
        public <E extends Exception> void warn(final LoggerConsumer<E> consumer) throws E {
            if (isWarnEnabled()) {
                consumer.accept(this);
            }
        }

        @Override
        public boolean isErrorEnabled() {
            return delegate.isErrorEnabled();
        }

        @Override
        public void error(final String message) {
            delegate.error(message);
        }

        @Override
        public void error(final String format, final Object arg) {
            delegate.error(format, arg);
        }

        @Override
        public void error(final String format, final Object arg1, final Object arg2) {
            delegate.error(format, arg1, arg2);
        }

        @Override
        public void error(final String format, final Object... arguments) {
            delegate.error(format, arguments);
        }

        @Override
        public <E extends Exception> void error(final LoggerConsumer<E> consumer) throws E {
            if (isErrorEnabled()) {
                consumer.accept(this);
            }
        }

        @Override
        public void audit(final String message) {
            delegate.info(message);
        }

        @Override
        public void audit(final String format, final Object arg) {
            delegate.info(format, arg);
        }

        @Override
        public void audit(final String format, final Object arg1, final Object arg2) {
            delegate.info(format, arg1, arg2);
        }

        @Override
        public void audit(final String format, final Object... arguments) {
            delegate.info(format, arguments);
        }
    }
}
