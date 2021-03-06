package com.alibaba.dubbo.tracer.client.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.tracer.api.Span;
import com.alibaba.dubbo.tracer.api.TracingCollector;
import com.alibaba.dubbo.tracer.client.DstConstants;
import com.alibaba.dubbo.tracer.client.SyncTransfer;
import com.alibaba.dubbo.tracer.client.TracingCollectorFactory;

public class DefaultSyncTransfer implements SyncTransfer {

    private static Logger logger = LoggerFactory.getLogger(DefaultSyncTransfer.class);

    private Protocol protocol;

    private volatile TracingCollector collector;
    private volatile BlockingQueue<Span> queue;
    private volatile TransferTask transferTask;

    private volatile boolean inited=false;

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    private class TransferTask extends Thread {
        private List<Span> cacheList;
        private int flushSizeInner;

        private TransferTask(int flushSize) {
            cacheList = new ArrayList<Span>();
            flushSizeInner = flushSize;
            setName("Dst-span-transfer-task-thread");
        }

        @Override
        public void run() {
            while (!interrupted()) {
                try {
                    Span first = queue.take();
                    cacheList.add(first);
                    queue.drainTo(cacheList, flushSizeInner);
                    if(cacheList.size()<=0){
                        continue;
                    }
                    if(!inited&&collector==null){
                        TracingCollectorFactory tracingCollectorFactory = ExtensionLoader
                                .getExtensionLoader(TracingCollectorFactory.class)
                                .getExtension(ConfigUtils.getProperty(DstConstants.TRACING_COLLECTOR
                                        ,DstConstants.DEFAULT_COLLECTOR_TYPE));
                        collector =tracingCollectorFactory.getTracingCollector();
                        inited=true;
                    }
                    collector.push(cacheList);
                    cacheList.clear();
                } catch (InterruptedException e) {
                    logger.error("Dst-span-transfer-task-thread occur an error", e);
                }
            }
        }
    }



    public DefaultSyncTransfer() {
        queue = new ArrayBlockingQueue<Span>(Integer.parseInt(ConfigUtils.getProperty(DstConstants.FLUSH_SIZE_KEY,DstConstants.DEFAULT_FLUSH_SIZE)));
        transferTask = new TransferTask(Integer.parseInt(ConfigUtils.getProperty(DstConstants.QUEUE_SIZE_KEY,DstConstants.DEFAULT_BUFFER_QUEUE_SIZE)));
    }


    public void start() {
        transferTask.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                cancel();
            }
        });
    }

    public void cancel() {
        transferTask.interrupt();
    }

    public void syncSend(Span span) {
        try {
            queue.add(span);
        } catch (Exception e) {
            logger.error("span : ignore ..", e);
        }
    }
}
