package com.heima.schedule.test;

import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.schedule.service.TaskService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Set;

@SpringBootTest
@RunWith(SpringRunner.class)
public class RedisTest {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private TaskService taskService;

    @Test
    public void testList() {
        cacheService.lLeftPush("list_001", "hello, redis");
        String list001 = cacheService.lRightPop("list_001");
        System.out.println(list001);
    }

    @Test
    public void zset() {
//        cacheService.zAdd("zset_001", "hello", 1000);
//        cacheService.zAdd("zset_001", "world", 2000);
//        cacheService.zAdd("zset_001", "redis", 3000);
        Set<String> zset001 = cacheService.zRangeByScore("zset_001", 0, 2000);
        System.out.println(zset001);
        cacheService.lRightPop("list_001");
    }

    @Test
    public void test() {
        Task task = new Task();
        task.setTaskType(100);
        task.setPriority(50);
        task.setExecuteTime(System.currentTimeMillis()+500000);
        task.setParameters("task test".getBytes());
        taskService.addTask(task);
    }

    @Test
    public void testCancel() {
        taskService.cancelTask(1808692068443693057L);
    }

    @Test
    public void testPoll() {
        Task task = taskService.poll(100, 50);
        System.out.println(task);
    }

}
