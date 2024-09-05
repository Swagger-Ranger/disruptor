/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

/**
 * Called by the {@link RingBuffer} to pre-populate all the events to fill the RingBuffer.
 * EventFactory：用于初始化 RingBuffer 中的事件对象，是创建事件对象的工厂方法，提供空的事件实例。
 * EventTranslator：用于向事件对象中写入实际的业务数据，它负责将生产者的数据翻译并写入到 RingBuffer 的节点（事件对象）中。
 *
 * @see EventTranslator
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 *           事件实现，在交换或并行协调事件期间存储用于共享的数据。
 */
public interface EventFactory<T>
{
    /**
     * Implementations should instantiate an event object, with all memory already allocated where possible.
     *
     * @return T newly constructed event instance.
     */
    T newInstance();
}