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
 * Implementations translate (write) data representations into events claimed from the {@link RingBuffer}.
 * 实现将数据表示转换(写)入RingBuffer中声明的event。
 *
 * <p>When publishing to the RingBuffer, provide an EventTranslator. The RingBuffer will select the next available
 * event by sequence and provide it to the EventTranslator (which should update the event), before publishing
 * the sequence update.
 * 当发布到RingBuffer时，提供一个EventTranslator。在更新sequence之前，RingBuffer将下一个可用的sequence提供给EventTranslator(其会更新其中的event数据)。
 *
 * <p>
 * EventFactory：用于初始化 RingBuffer 中的事件对象，是创建事件对象的工厂方法，提供空的事件实例。
 * EventTranslator：用于向事件对象中写入实际的业务数据，它负责将生产者的数据翻译并写入到 RingBuffer 的节点（事件对象）中。
 * 而EventTranslator则是在ringbuffer中发布数据使用的，因为发布对象中的publish方法只能传入EventTranslator
 *
 * @see EventFactory
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 *
 */
public interface EventTranslator<T>
{
    /**
     * 将数据写入对应槽位的Event节点中
     * Translate a data representation into fields set in given event
     *
     * @param event    into which the data should be translated.
     * @param sequence that is assigned to event.
     */
    void translateTo(T event, long sequence);
}
