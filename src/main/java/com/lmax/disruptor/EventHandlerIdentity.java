package com.lmax.disruptor;

/**
 * 一个空接口，没有任何方法，它的设计目的主要是为了给事件处理器 (EventHandler) 提供一种标记功能。
 * 但在某些特定场景中，可以通过类型标记来实现更灵活和可扩展的设计。
 */
public interface EventHandlerIdentity
{
}
