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
package org.apache.winegrower.scanner;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.function.Predicate;

import org.apache.winegrower.Ripener;

public class KnownJarsFilter implements Predicate<String> {
    private final Collection<String> forceIncludes = new HashSet<String>() {{
        add("winegrower-cdi");
    }};
    private final Collection<String> excludes = new HashSet<String>() {{
        add("activation-");
        add("activeio-");
        add("activemq-");
        add("aether-");
        add("akka-");
        add("ant-");
        add("antlr-");
        add("aopalliance-");
        add("ApacheJMeter");
        add("animal-sniffer");
        add("apiguardian-");
        add("args4j-");
        add("arquillian-");
        add("asciidoctor");
        add("asm-");
        add("async-http-client-");
        add("avalon-framework-");
        add("axis");
        add("batchee");
        add("batik-");
        add("bcprov-");
        add("bootstrap");
        add("bsf-");
        add("bval");
        add("c3p0-");
        add("cassandra-driver-core");
        add("catalina");
        add("cdi-api");
        add("cglib-");
        add("charsets.jar");
        add("commons");
        add("cryptacular-");
        add("cssparser-");
        add("cxf-");
        add("deploy");
        add("derby");
        add("dom4j");
        add("ecj-");
        add("eclipselink-");
        add("ehcache-");
        add("el-api");
        add("FastInfoset");
        add("freeemarker-");
        add("fusemq-leveldb-");
        add("geronimo-");
        add("google-");
        add("gpars-");
        add("gragent.jar");
        add("groovy-");
        add("gson-");
        add("guava-");
        add("guice-");
        add("h2-");
        add("hamcrest-");
        add("hawt");
        add("hibernate-");
        add("howl-");
        add("hsqldb-");
        add("htmlunit-");
        add("httpclient-");
        add("httpcore-");
        add("icu4j-");
        add("idb-");
        add("idea_rt.jar");
        add("istack-commons-runtime-");
        add("ivy-");
        add("jackson-");
        add("janino-");
        add("jansi-");
        add("jasper");
        add("jasypt-");
        add("java");
        add("jaxb-");
        add("jaxp-");
        add("jbake-");
        add("jboss");
        add("jce.jar");
        add("jcommander-");
        add("jersey-");
        add("jettison-");
        add("jetty-");
        add("jfr.jar");
        add("jfxrt.jar");
        add("jline");
        add("jmdns-");
        add("jna-");
        add("jnr-");
        add("joda-time-");
        add("johnzon-");
        add("jolokia-");
        add("jruby-");
        add("json");
        add("jsoup-");
        add("jsp");
        add("jsr");
        add("jsse.jar");
        add("jul");
        add("junit");
        add("jython-");
        add("kahadb-");
        add("kotlin-runtime");
        add("leveldb");
        add("log");
        add("lombok-");
        add("lucene");
        add("management-agent.jar");
        add("maven-");
        add("mbean-annotation-api-");
        add("meecrowave-");
        add("microprofile-");
        add("mimepull-");
        add("mina-");
        add("mqtt-client-");
        add("multiverse-core-");
        add("myfaces-");
        add("mysql-");
        add("neethi-");
        add("nekohtml-");
        add("netty-");
        add("openjpa-");
        add("openmdx-");
        add("opensaml-");
        add("opentest4j-");
        add("openwebbeans-");
        add("openws-");
        add("ops4j-");
        add("org.apache.aries");
        add("org.eclipse.");
        add("org.jacoco.agent");
        add("org.junit.");
        add("org.osgi.");
        add("orient-");
        add("oro-");
        add("pax");
        add("PDFBox");
        add("plexus-");
        add("plugin.jar");
        add("poi-");
        add("qdox-");
        add("quartz");
        add("resources.jar");
        add("rhino-");
        add("rmock-");
        add("rt.jar");
        add("saaj-");
        add("sac-");
        add("scala");
        add("scannotation-");
        add("serializer-");
        add("serp-");
        add("servlet-api-");
        add("shrinkwrap-");
        add("sisu-guice");
        add("sisu-inject");
        add("slf4j-");
        add("smack");
        add("snappy-");
        add("spring-");
        add("sshd-");
        add("stax");
        add("sunec.jar");
        add("surefire-");
        add("swizzle-");
        add("sxc-");
        add("testng-");
        add("tomcat");
        add("tomee-");
        add("tools.jar");
        add("twitter4j-");
        add("validation-api-");
        add("velocity-");
        add("wagon-");
        add("webbeans");
        add("websocket");
        add("winegrower");
        add("woodstox-core-");
        add("ws-commons-util-");
        add("wsdl4j-");
        add("wss4j-");
        add("wstx-asl-");
        add("xalan-");
        add("xbean-");
        add("xercesImpl-");
        add("xml");
        add("XmlSchema-");
        add("xstream-");
        add("zipfs.jar");
        add("ziplock-");
    }};

    public KnownJarsFilter(final Ripener.Configuration config) {
        ofNullable(config.getScanningIncludes()).ifPresent(i -> {
            forceIncludes.clear();
            forceIncludes.addAll(i.stream().map(String::trim).filter(j -> !j.isEmpty()).collect(toSet()));
        });
        ofNullable(config.getScanningExcludes())
                .ifPresent(i -> excludes.addAll(i.stream().map(String::trim).filter(j -> !j.isEmpty()).collect(toSet())));
    }

    @Override
    public boolean test(final String jarName) {
        return forceIncludes.stream().anyMatch(jarName::startsWith) || excludes.stream().noneMatch(jarName::startsWith);
    }
}
