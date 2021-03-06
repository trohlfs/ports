/*
 * Copyright 2018-2020 Tim Rohlfs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.timux.ports;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

class DomainManager {

    private static final String DEFAULT_DOMAIN_NAME = "default";
    private static final Domain DEFAULT_DOMAIN = new Domain(DEFAULT_DOMAIN_NAME, DispatchPolicy.SYNCHRONOUS, SyncPolicy.COMPONENT);

    private static Map<Object, Domain> instanceDomains = new WeakHashMap<>();
    private static Map<Class<?>, Domain> classDomains = new HashMap<>();
    private static Map<String, Domain> packageDomains = new HashMap<>();

    private static int currentVersion = 0;

    static synchronized Domain getDomain(Object instance) {
        String pkg = instance.getClass().getPackage().getName();

        Domain domain;

        for (;;) {
            domain = packageDomains.get(pkg);

            if (domain != null) {
                return domain;
            }

            int dotIndex = pkg.lastIndexOf('.');

            if (dotIndex < 0) {
                break;
            }

            pkg = pkg.substring(0, dotIndex);
        }

        domain = classDomains.get(instance.getClass());

        if (domain != null) {
            return domain;
        }

        domain = instanceDomains.get(instance);

        return domain != null ? domain : DEFAULT_DOMAIN;
    }

    static synchronized void register(Object instance, Domain domain) {
        instanceDomains.put(instance, domain);
        currentVersion++;
    }

    static synchronized void register(Class<?> clazz, Domain domain) {
        classDomains.put(clazz, domain);
        currentVersion++;
    }

    static synchronized void register(String pkg, Domain domain) {
        packageDomains.put(pkg, domain);
        currentVersion++;
    }

    static int getCurrentVersion() {
        return currentVersion;
    }

    static synchronized void invalidate() {
        currentVersion++;
    }

    static synchronized void release() {
        instanceDomains.values().forEach(Domain::release);
        classDomains.values().forEach(Domain::release);
        packageDomains.values().forEach(Domain::release);

        instanceDomains.clear();
        classDomains.clear();
        packageDomains.clear();
        currentVersion++;
    }

    static synchronized void awaitQuiescence() {
        for (Map.Entry<Object, Domain> e : instanceDomains.entrySet()) {
            e.getValue().awaitQuiescence();
        }
    }
}
