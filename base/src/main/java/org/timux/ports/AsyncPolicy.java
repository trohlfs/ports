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

/**
 * An enum class providing options for the way asynchronicity is handled.
 *
 * @since 0.5.0
 */
public enum AsyncPolicy {

    /**
     * Specifies that message processing is subject to mutual exclusion w.r.t. to the
     * complete domain.
     */
    DOMAIN_SYNC,

    /**
     * Specifies that message processing is subject to mutual exclusion w.r.t. to
     * individual components.
     */
    COMPONENT_SYNC,

    /**
     * Specifies that message processing is subject to mutual exclusion w.r.t. to
     * individual IN ports.
     */
    PORT_SYNC,

    /**
     * Specifies that message processing is subject to mutual exclusion w.r.t. to
     * individual components. In addition, all messages have to be processed within
     * the thread of the sender.
     *
     * <p> This is the default setting.
     */
    COMPONENT_SYNC_SAME_THREAD,

    /**
     * Specifies that message processing is subject to mutual exclusion w.r.t. to the
     * complete domain. In addition, all messages have to be processed within
     * the thread of the sender.
     */
    DOMAIN_SYNC_SAME_THREAD,

    /**
     * Specifies that message processing is subject to mutual exclusion w.r.t. to
     * individual IN ports. In addition, all messages have to be processed within
     * the thread of the sender.
     */
    PORT_SYNC_SAME_THREAD,

    /**
     * Specifies that messages may be processed asynchronously without any synchronization.
     *
     * <p> <strong>This is a dangerous setting.</strong> Take care that all components in the domain are
     * thread-safe. Note that it is not enough to ensure that each individual IN port is thread-safe
     * because the interplay of multiple thread-safe IN ports can still lead to race conditions.
     */
    ASYNCHRONOUS
}
