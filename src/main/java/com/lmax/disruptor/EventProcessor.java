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
 * An EventProcessor needs to be an implementation of a runnable that will poll for events from the {@link RingBuffer}
 * using the appropriate wait strategy.  It is unlikely that you will need to implement this interface yourself.
 * Look at using the {@link EventHandler} interface along with the pre-supplied BatchEventProcessor in the first
 * instance.
 *
 * <p>An EventProcessor will generally be associated with a Thread for execution.
 *
 * <p> 注意：EventHandler才是实际的消费者需要自己实现，而EventProcessor则只是一个管理接口，不需要自己实现；
 * EventProcessor的实现类往往也是提供对消费过程的管理，实际的事件处理是委托给 EventHandler
 * <p>
 * EventProcessor和{@link EventHandler}是一对一的: EventProcessor 是事件处理的管理者，它将事件传递给 EventHandler 进行实际处理。
 * 但 EventProcessor才是主要负责控制流，其继承Runnable可以在独立的线程中运行，开始启动消费也是由EventProcessor来实现runable在线程中调用run方法运行的。
 */
public interface EventProcessor extends Runnable
{
    /**
     * Get a reference to the {@link Sequence} being used by this {@link EventProcessor}.
     *
     * @return reference to the {@link Sequence} for this {@link EventProcessor}
     */
    Sequence getSequence();

    /**
     * Signal that this EventProcessor should stop when it has finished consuming at the next clean break.
     * It will call {@link SequenceBarrier#alert()} to notify the thread to check status.
     */
    void halt();

    /**
     * @return whether this event processor is running or not
     * Implementations should ideally return false only when the associated thread is idle.
     */
    boolean isRunning();
}
