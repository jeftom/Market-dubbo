package com.alibaba.dubbo.monitor.simple.storage.disruptor;

import com.alibaba.dubbo.monitor.simple.storage.model.Statistics;

/**
 * StatisticsEvent
 * Created by bieber.bibo on 16/4/14
 */

public class StatisticsEvent {

    private Statistics statistics;

    public void set(Statistics statistics){
        this.statistics = statistics;
    }

    public Statistics get(){
        return statistics;
    }
}
