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

    private final Deque<Task> queue = new ArrayDeque<>();
    private final Executor workerExecutor;

    MessageQueue(String name, int maxNumberOfThreads) {
        workerExecutor = new Executor(this, "ports-worker-" + name, maxNumberOfThreads);
    }

    void enqueue(Consumer eventPort, Object payload) {
        Task task = new Task(eventPort, payload);

        synchronized (queue) {
            queue.add(task);
            workerExecutor.onNewTaskAvailable(queue.size());
        }
    }

    <I, O> PortsFuture<O> enqueue(Function<I, O> requestPort, I payload) {
        Task task = new Task(requestPort, payload);

        int queueSize;

        synchronized (queue) {
            queue.add(task);
            queueSize = queue.size();
        }

        workerExecutor.onNewTaskAvailable(queueSize);

        return new PortsFuture<>(task);
    }

    Task poll() {
        synchronized (queue) {
            return queue.poll();
        }
    }

    void awaitQuiescence() {
        workerExecutor.awaitQuiescence();
    }
}
