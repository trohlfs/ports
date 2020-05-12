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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings({"rawtypes", "unchecked"})
class MessageQueue {

    private static class DispatchThread extends Thread {

        public DispatchThread() {
            setDaemon(true);
            setName("ports-dispatcher");
            start();
        }

        @Override
        public void run() {
            while (true) {
                Task task;

                synchronized (messageQueue) {
                    while (messageQueue.isEmpty()) {
                        try {
                            messageQueue.wait();
                        } catch (InterruptedException e) {
                            Ports.printWarning("dispatcher has been interrupted");
                            return;
                        }
                    }

                    task = messageQueue.poll();
                }

                workerExecutor.submit(task);
            }
        }
    }

    private static final Deque<Task> messageQueue = new ArrayDeque<>();
    private static final DispatchThread dispatchThread = new DispatchThread();
    private static final Executor workerExecutor = new Executor("ports-worker");
    private static final Executor asyncExecutor = new Executor("ports-async");
    private static SyncPolicy syncPolicy = SyncPolicy.COMPONENT_SYNC;

    static void enqueueSync(Consumer eventPort, Object payload) {
        if (workerExecutor.isOwnThread(Thread.currentThread())) {
            eventPort.accept(payload);
            return;
        }

        Task task = new Task(eventPort, payload);

        synchronized (messageQueue) {
            messageQueue.add(task);
            messageQueue.notify();
        }

        task.waitForResponse();
    }

    static void enqueueAsync(Consumer eventPort, Object payload) {
        Task task = new Task(eventPort, payload);
        asyncExecutor.submit(task);
    }

    static <I, O> PortsFuture<O> enqueueSync(Function<I, O> requestPort, I payload) {
        if (workerExecutor.isOwnThread(Thread.currentThread())) {
            try {
                return new PortsFuture<>(requestPort.apply(payload));
            } catch (Throwable t) {
                throw new ExecutionException(t);
            }
        }

        Task task = new Task(requestPort, payload);

        synchronized (messageQueue) {
            messageQueue.add(task);
            messageQueue.notify();
        }

        return new PortsFuture<>((O) task.waitForResponse());
    }

    static <I, O> PortsFuture<O> enqueueAsync(Function<I, O> requestPort, I payload) {
        Task task = new Task(requestPort, payload);
        asyncExecutor.submit(task);
        return new PortsFuture<>(task);
    }

    static void awaitQuiescence() {
        do {
            workerExecutor.awaitQuiescence();
            asyncExecutor.awaitQuiescence();
        } while (!workerExecutor.isQuiescent() || !asyncExecutor.isQuiescent());
    }

    static void setSyncPolicy(SyncPolicy syncPolicy) {
        MessageQueue.syncPolicy = syncPolicy;
    }

    static SyncPolicy getSyncPolicy() {
        return syncPolicy;
    }
}
