package com.zw.sqliteTest.util;

import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class WriteTaskExecutor {
    // 单线程线程池本质上就是一个有界队列+单工作线程
    // 提交的任务会被排队，依次由唯一线程串行执行，保证写操作顺序和无并发写锁竞争
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    public void submit(Runnable task) {
        writeExecutor.execute(task);
    }

    @PreDestroy
    public void shutdown() {
        writeExecutor.shutdown();
    }
}
