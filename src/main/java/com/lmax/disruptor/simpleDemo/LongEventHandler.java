package com.lmax.disruptor.simpleDemo;

import com.lmax.disruptor.EventHandler;

/**
 * @author liufei
 **/
public class LongEventHandler implements EventHandler<LongEvent> {
    @Override
    public void onEvent(LongEvent event, long sequence, boolean endOfBatch) {
        System.out.println("啊啊啊 Event: " + event.getValue());
    }
}
