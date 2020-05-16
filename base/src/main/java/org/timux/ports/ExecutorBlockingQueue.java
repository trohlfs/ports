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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class ExecutorBlockingQueue {

    private static final long IDLE_LIFETIME_MS = 10000;

    // The following TEST_API fields must not be private or final because they are
    // modified by the tests to achieve deterministic behavior.
    static int TEST_API_MAX_NUMBER_OF_THREADS = -1;
    static long TEST_API_IDLE_LIFETIME_MS = -1L;

    class WorkerThread extends Thread implements Thread.UncaughtExceptionHandler {

        public WorkerThread(ThreadGroup threadGroup) {
            super(threadGroup, threadGroup.getName() + "-" + nextThreadId.getAndIncrement());
            setDaemon(true);
            setUncaughtExceptionHandler(this);
            start();
        }

        @Override
        public void run() {
            while (true) {
                Task task = dispatcher.poll(idleLifetimeMs, TimeUnit.MILLISECONDS);

                if (task == null) {
                    synchronized (threadPool) {
                        if (threadPool.size() == 1) {
                            // Do not kill this thread because it is the only one left.
                            continue;
                        }

                        threadPool.remove(this);
                        return;
                    }
                }

                synchronized (threadPool) {
                    numberOfBusyThreads++;
                }

                // Exception handling is done within the task, so not required here.
                task.run();

                synchronized (threadPool) {
                    numberOfBusyThreads--;
                }
            }
        }

        @Override
        public void uncaughtException(Thread thread, Throwable t) {
            // This should never happen because we catch all exceptions.

            synchronized (threadPool) {
                threadPool.remove(thread);
            }

            System.err.println("Thread [" + thread.getName() + "] died because of uncaught exception:");
            t.printStackTrace();
        }
    }

    private final List<WorkerThread> threadPool = new ArrayList<>();
    private final ThreadGroup threadGroup;
    private final DispatcherBlockingQueue dispatcher;
    private final AtomicInteger nextThreadId = new AtomicInteger();
    private final int maxThreadPoolSize;
    private final long idleLifetimeMs;

    private int numberOfBusyThreads = 0;

    ExecutorBlockingQueue(DispatcherBlockingQueue dispatcher, String threadGroupName, int maxThreadPoolSize) {
        Random random = new Random();

        this.dispatcher = dispatcher;
        this.threadGroup = new ThreadGroup(threadGroupName + "-" + random.nextInt());
        this.maxThreadPoolSize = TEST_API_MAX_NUMBER_OF_THREADS < 0 ? maxThreadPoolSize : TEST_API_MAX_NUMBER_OF_THREADS;
        this.idleLifetimeMs = TEST_API_IDLE_LIFETIME_MS < 0 ? IDLE_LIFETIME_MS : TEST_API_IDLE_LIFETIME_MS;
    }

    void onNewTaskAvailable(int numberOfTasksInQueue) {
        synchronized (threadPool) {
            if (numberOfTasksInQueue > threadPool.size() - numberOfBusyThreads && threadPool.size() < maxThreadPoolSize) {
                threadPool.add(new WorkerThread(threadGroup));
            }
        }
    }

    void awaitQuiescence() {
        for (int numberOfRuns = 0; ; numberOfRuns = (numberOfRuns + 1) & 0xffffff) {
            synchronized (threadPool) {
                if (numberOfBusyThreads == 0) {
                    return;
                }
            }

            try {
                Thread.sleep(numberOfRuns < 10 ? 10 : (numberOfRuns < 50 ? 100 : 333));
            } catch (InterruptedException e) {
                Ports.printWarning("awaitQuiescence has been interrupted");
                return;
            }
        }
    }

    boolean isQuiescent() {
        synchronized (threadPool) {
            return numberOfBusyThreads == 0;
        }
    }

    int getNumberOfThreads() {
        synchronized (threadPool) {
            return threadPool.size();
        }
    }
}
