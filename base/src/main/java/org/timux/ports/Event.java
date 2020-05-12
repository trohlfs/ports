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

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A class that represents an OUT port with fire-and-forget semantics. An Event OUT port may be connected to
 * an arbitrary number of IN ports.
 *
 * @param <T> The type of the payload.
 *
 * @see Request
 *
 * @author Tim Rohlfs
 * @since 0.1
 */
public class Event<T> {

    private static class PortEntry<T> {

        Consumer<T> port;
        WeakReference<?> receiver;
        boolean isAsyncReceiver;

        PortEntry(Consumer<T> port, Object receiver, boolean isAsyncReceiver) {
            this.port = port;
            this.receiver = new WeakReference<>(receiver);
            this.isAsyncReceiver = isAsyncReceiver;
        }
    }

    private final List<PortEntry<T>> ports = new ArrayList<>(4);
    private Map<Method, Map<WeakReference<?>, Consumer<T>>> portMethods = null;
    private String eventTypeName;
    private Object owner;

    public Event() {
        //
    }

    Event(String eventTypeName, Object owner) {
        this.eventTypeName = eventTypeName;
        this.owner = owner;
    }

    /**
     * Connects this OUT port to the given IN port. In case this OUT port is already connected to any IN ports,
     * the new connection will be added to the existing ones.
     *
     * @param port The IN port that this OUT port should be connected to. Must not be null.
     */
    private synchronized void connect(Consumer<T> port, Object receiver, boolean isAsyncReceiver) {
        if (port == null) {
            throw new IllegalArgumentException("port must not be null");
        }

        ports.add(new PortEntry<>(port, receiver, isAsyncReceiver));
    }

    synchronized void connect(Method portMethod, Object methodOwner, EventWrapper eventWrapper) {
        if (portMethod == null) {
            throw new IllegalArgumentException("port must not be null");
        }

        cleanUpGarbageCollectedConnections();

        if (portMethods == null) {
            portMethods = new HashMap<>();
        }

        Map<WeakReference<?>, Consumer<T>> portOwners = portMethods.computeIfAbsent(portMethod, k -> new HashMap<>(4));

        WeakReference<?> key = new WeakReference<>(methodOwner);

        if (eventWrapper == null) {
            portOwners.put(
                    key,
                    x -> {
                        try {
                            Object owner = key.get();

                            if (owner != null) {
                                portMethod.invoke(owner, x);
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } else {
            portOwners.put(
                    key,
                    x -> eventWrapper.execute(() -> {
                        Object owner = key.get();

                        if (owner != null) {
                            try {
                                portMethod.invoke(owner, x);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }));
        }

        connect(portOwners.get(key), methodOwner, portMethod.getDeclaredAnnotation(AsyncPort.class) != null);
    }

    /**
     * Connects this OUT port to the given IN port. In case this OUT port is already connected to any IN ports,
     * the new connection will be added to the existing ones.
     *
     * @param port The IN port that this OUT port should be connected to.
     */
    void connect(QueuePort<T> port, Object portOwner) {
        connect(port::add, portOwner, false);
    }

    /**
     * Connects this OUT port to the given IN port. In case this OUT port is already connected to any IN ports,
     * the new connection will be added to the existing ones.
     *
     * @param port The IN port that this OUT port should be connected to.
     */
    void connect(StackPort<T> port, Object portOwner) {
        connect(port::push, portOwner, false);
    }

    /**
     * Disconnects this OUT port from the given IN port.
     */
    synchronized void disconnect(Consumer<T> port) {
        int index = -1;

        for (int i = ports.size() - 1; i >= 0; i--) {
            if (ports.get(i).port == port) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            ports.remove(index);
        }
    }

    synchronized void disconnect(Method portMethod, Object methodOwner) {
        Map<WeakReference<?>, Consumer<T>> portOwners = portMethods.get(portMethod);

        if (portOwners == null) {
            return;
        }

        WeakReference<?> key = getPortOwnersKeyOf(methodOwner, portOwners);

        if (key != null) {
            disconnect(portOwners.get(key));
        }

        portOwners.remove(key);

        if (portOwners.isEmpty()) {
            portMethods.remove(portMethod);
        }
    }

    private synchronized WeakReference<?> getPortOwnersKeyOf(Object portOwner, Map<WeakReference<?>, Consumer<T>> portOwners) {
        for (Map.Entry<WeakReference<?>, Consumer<T>> e : portOwners.entrySet()) {
            if (e.getKey().get() == portOwner) {
                return e.getKey();
            }
        }

        // owner has been garbage-collected.
        return null;
    }

    private synchronized void cleanUpGarbageCollectedConnections() {
        if (portMethods == null || portMethods.isEmpty()) {
            return;
        }

        Map<WeakReference<?>, Method> collectedReferences = new HashMap<>();

        for (Map.Entry<Method, Map<WeakReference<?>, Consumer<T>>> e : portMethods.entrySet()) {
            for (Map.Entry<WeakReference<?>, Consumer<T>> ee : e.getValue().entrySet()) {
                if (ee.getKey().get() == null) {
                    collectedReferences.put(ee.getKey(), e.getKey());
                }
            }
        }

        for (Map.Entry<WeakReference<?>, Method> e : collectedReferences.entrySet()) {
            WeakReference<?> reference = e.getKey();
            Method method = e.getValue();

            disconnect(portMethods.get(method).get(reference));

            portMethods.get(method).remove(reference);

            if (portMethods.get(method).isEmpty()) {
                portMethods.remove(method);
            }
        }
    }

    synchronized void triggerWST(T payload) {
        if (Protocol.areProtocolsActive) {
            Protocol.onDataSent(eventTypeName, owner, payload);
        }

        final List<PortEntry<T>> p = ports;

        int i = p.size();

        if (i == 0) {
            Ports.printWarning(String.format(
                    "event %s was fired by component %s but there is no receiver",
                    eventTypeName,
                    owner.getClass().getName()));
            return;
        }

        for (i--; i >= 0; i--) {
            p.get(i).port.accept(payload);
        }
    }

    /**
     * Sends the given payload to the connected IN port(s). For each IN port, the event will be handled asynchronously
     * if the IN port is an {@link AsyncPort} and if the {@link SyncPolicy} allows it; if not, the event will be
     * handled synchronously, but not necessarily within the thread of the caller.
     *
     * @param payload The payload to be sent.
     *
     * @see #trigger
     * @see AsyncPort
     * @see SyncPolicy
     *
     * @since 0.5.0
     */
    public synchronized void trigger(T payload) {
        Domain senderDomain = DomainManager.getDomain(owner);

        if (Protocol.areProtocolsActive) {
            Protocol.onDataSent(eventTypeName, owner, payload);
        }

        final List<PortEntry<T>> p = ports;

        int i = p.size();

        if (i == 0) {
            Ports.printWarning(String.format(
                    "event %s was fired by component %s but there is no receiver",
                    eventTypeName,
                    owner.getClass().getName()));
            return;
        }

        for (i--; i >= 0; i--) {
            Domain receiverDomain = DomainManager.getDomain(p.get(i).receiver);
            WeakReference<?> receiverRef = p.get(i).receiver;
            Consumer<T> port = p.get(i).port;

            Consumer<T> portFunction = x -> {
                Object receiver = receiverRef.get();

                if (receiver == null) {
                    return;
                }

                switch (receiverDomain.getSyncPolicy()) {
                case ASYNCHRONOUS:
                    port.accept(x);
                    break;

                case COMPONENT_SYNC:
                    synchronized (receiverRef) {
                        port.accept(x);
                    }
                    break;

                case DOMAIN_SYNC:
                    synchronized (receiverDomain) {
                        port.accept(x);
                    }
                    break;

                default:
                    throw new IllegalStateException("unhandled sync policy: " + receiverDomain.getSyncPolicy());
                }
            };

            switch (receiverDomain.getDispatchPolicy()) {
            case SAME_THREAD:
                portFunction.accept(payload);
                break;

            case PARALLEL:
                MessageQueue.enqueueAsync(portFunction, payload);
                break;

            default:
                throw new IllegalStateException("unhandled dispatch policy: " + receiverDomain.getDispatchPolicy());
            }
        }
    }

    /**
     * Returns true if this OUT port is connected to an IN port, false otherwise.
     */
    public synchronized boolean isConnected() {
        cleanUpGarbageCollectedConnections();
        return !ports.isEmpty();
    }
}
