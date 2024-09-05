package com.lmax.disruptor.simpleDemo;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * @author liufei
 **/
public class DisruptorTest {

    public static void main(String[] args) throws Exception {
        // 创建工厂
        LongEventFactory factory = new LongEventFactory();

        // 创建 RingBuffer 大小
        int bufferSize = 1024;

        // 创建 Disruptor
        Disruptor<LongEvent> disruptor = new Disruptor<>(factory, bufferSize, DaemonThreadFactory.INSTANCE);

        // 连接事件处理器
        disruptor.handleEventsWith(new LongEventHandler());

        // 启动 Disruptor
        disruptor.start();

        // 获取 RingBuffer 用于发布事件
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        // 发布事件
        long sequence = ringBuffer.next();
        try {
            LongEvent event = ringBuffer.get(sequence);
            event.setValue(100L);
        } finally {
            // 每个Event其实都在RingBuffer初始化好了，生产者只是修改event上的数据，然后publish
            ringBuffer.publish(sequence);
        }

        disruptor.shutdown();
    }
}
