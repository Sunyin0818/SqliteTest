package com.zw.sqliteTest.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.zw.sqliteTest.entity.Student;
import com.zw.sqliteTest.mapper.StudentMapper;
import com.zw.sqliteTest.service.TestService;
import com.zw.sqliteTest.util.WriteTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class TestServiceImpl implements TestService {
    @Autowired
    private StudentMapper studentMapper;
    @Autowired
    private WriteTaskExecutor writeTaskExecutor;

    @Override
    public String insertTest(Integer num) {
        String result = "";
        CountDownLatch cdt = new CountDownLatch(num);

        long starttime = System.currentTimeMillis();

        studentMapper.delete(null);
        for (int i = 0; i < num; i++) {
            int finalI = i;
            writeTaskExecutor.submit(() -> {
                try {
                    Student student = new Student();
                    student.setId(finalI);
                    student.setName("zw" + finalI);
                    student.setAge(finalI * 2);
                    studentMapper.insert(student);
                    if (finalI - 3 >= 0) {
                        Student student2 = studentMapper.selectById(finalI - 3);
                        if (student2 != null) {
                            log.info("{} student2.getId:{}", Thread.currentThread().getName(), student2.getId());
                        }
                    }
                }
                finally {
                    cdt.countDown();
                }
            });
        }
        try {
            cdt.await();
        }
        catch (InterruptedException e) {
            log.error("等待线程异常", e);
        }
        Double time = Convert.toDouble(DateUtil.spendMs(starttime));
        result = "写操作执行完" + num + "个记录耗时:"
                + time + "毫秒" + ", 每条记录大概需要" + NumberUtil.decimalFormat("#.##", time / num) + "毫秒" + ",tps大概:" + NumberUtil.decimalFormat("#.##", num / time * 1000);
        log.info(result);
        return result;
    }

    @Override
    public String selectTest(Integer num) {
        String result = "";
        CountDownLatch cdt = new CountDownLatch(num);
        int busThreadNum = Runtime.getRuntime().availableProcessors() * 2;
        log.info("业务线程数:{}", busThreadNum);
        ExecutorService service = Executors.newFixedThreadPool(busThreadNum);
        long starttime = System.currentTimeMillis();
        Integer rowcount = studentMapper.selectCount(null);
        for (int i = 0; i < num; i++) {
            int finalI = i;
            service.execute(() -> {
                try {
                    // 随机获取学生数据
                    Student student = studentMapper.selectById(RandomUtil.randomInt(rowcount));
                    log.info(student.toString());
                }
                catch (Exception e) {
                    log.error("{} 执行任务:{} 失败", Thread.currentThread().getName(), finalI, e);
                }
                finally {
                    cdt.countDown();
                }
            });
        }
        try {
            cdt.await();
        }
        catch (InterruptedException e) {
            log.error("等待线程异常", e);
        }
        service.shutdown(); // 释放线程池资源
        Double time = Convert.toDouble(DateUtil.spendMs(starttime));
        result = "随机读主键操作执行完" + num + "个记录耗时:"
                + time + "毫秒" + ", 每条记录大概需要" + NumberUtil.decimalFormat("#.##", time / num) + "毫秒" + ",qps大概:" + NumberUtil.decimalFormat("#.##", num / time * 1000);
        log.info(result);
        return result;
    }

    // 新增学生（异步串行写）
    public void addStudent(Student student) {
        writeTaskExecutor.submit(() -> {
            studentMapper.insert(student);
        });
    }

    // 根据ID删除学生（异步串行写）
    public void deleteStudentById(Integer id) {
        writeTaskExecutor.submit(() -> {
            studentMapper.deleteById(id);
        });
    }

    // 更新学生信息（异步串行写）
    public void updateStudent(Student student) {
        writeTaskExecutor.submit(() -> {
            studentMapper.updateById(student);
        });
    }

    // 对于只读操作，加 @Transactional(readOnly = true) 可以提升只读事务的语义清晰度
    // 但对于 SQLite，readOnly 主要是语义提示，对性能和锁影响有限
    // 建议在 Service 层读方法加上，便于团队理解和后续迁移

    @Transactional(readOnly = true)
    public Student getStudentById(Integer id) {
        return studentMapper.selectById(id);
    }

    @Transactional(readOnly = true)
    public java.util.List<Student> getAllStudents() {
        return studentMapper.selectList(null);
    }

    /**
     * 需要先写后读的任务，建议将写操作通过writeTaskExecutor.submit提交，
     * 写操作完成后再执行读操作（可用Future或回调），保证写已落库再读。
     */
    public Student addAndGetStudent(Student student) {
        final CountDownLatch latch = new CountDownLatch(1);
        final int[] generatedId = new int[1];
        // 写操作串行提交
        writeTaskExecutor.submit(() -> {
            studentMapper.insert(student);
            generatedId[0] = student.getId();
            latch.countDown();
        });
        try {
            latch.await(); // 等待写操作完成
        }
        catch (InterruptedException e) {
            log.error("等待写操作异常", e);
            Thread.currentThread().interrupt();
        }
        // 写完成后直接读
        return studentMapper.selectById(generatedId[0]);
    }

    /**
     * 需要先读后写的任务，读操作可直接并发，写操作仍需通过writeTaskExecutor.submit提交
     */
    public void readThenUpdate(Integer id, String newName) {
        Student student = studentMapper.selectById(id); // 直接读
        if (student != null) {
            student.setName(newName);
            writeTaskExecutor.submit(() -> studentMapper.updateById(student)); // 串行写
        }
    }
}