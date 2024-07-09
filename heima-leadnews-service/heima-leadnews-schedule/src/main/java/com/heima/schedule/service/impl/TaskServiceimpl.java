package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.constans.ScheduleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import javax.annotation.PostConstruct;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class TaskServiceimpl implements TaskService {

    @Autowired
    private TaskinfoMapper taskinfoMapper;

    @Autowired
    private TaskinfoLogsMapper taskinfoLogsMapper;

    @Autowired
    private CacheService cacheService;

    @Override
    public long addTask(Task task) {
        // 添加任务到数据库
        boolean success = addTaskToDb(task);
        // 添加任务到redis
        if (success) {
            addTaskToCache(task);
        }
        return task.getTaskId();
    }

    private void addTaskToCache(Task task) {
        String key = task.getTaskType() + "_" + task.getPriority();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        long timeInMillis = calendar.getTimeInMillis();

        // 如果任务小于等于当前时间，存入list
        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            cacheService.lLeftPush(ScheduleConstants.TOPIC + key, JSON.toJSONString(task));
        } else if (task.getExecuteTime() <= timeInMillis) {
            // 如果大于当前时间小于等于预设时间，存入zset
            cacheService.zAdd(ScheduleConstants.FUTURE + key, JSON.toJSONString(task), task.getExecuteTime());
        }
        System.out.println("added to cache");
    }

    private boolean addTaskToDb(Task task) {
        boolean flag = false;

        // 保存任务
        Taskinfo taskinfo = new Taskinfo();
        BeanUtils.copyProperties(task, taskinfo);
        taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
        taskinfoMapper.insert(taskinfo);

        task.setTaskId(taskinfo.getTaskId());

        // 保存任务日志
        TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
        BeanUtils.copyProperties(taskinfo, taskinfoLogs);
        taskinfoLogs.setVersion(1);
        taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);
        taskinfoLogsMapper.insert(taskinfoLogs);

        flag = true;
        return flag;
    }

    @Override
    public boolean cancelTask(Long taskId) {
        boolean flag = false;
        // 删除任务，更新任务日志
        Task task = updateDb(taskId, ScheduleConstants.CANCELLED);
        // 删除redis数据
        if (task != null) {
            removeTaskFromCache(task);
            flag = true;
        }
        return flag;
    }

    private void removeTaskFromCache(Task task) {
        String key = task.getTaskType() + "_" + task.getPriority();
        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            cacheService.lRemove(ScheduleConstants.TOPIC + key, 0, JSON.toJSONString(task));
        } else {
            cacheService.zRemove(ScheduleConstants.FUTURE + key, JSON.toJSONString(task));
        }
    }

    private Task updateDb(Long taskId, int status) {
        taskinfoMapper.deleteById(taskId);

        TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
        taskinfoLogs.setStatus(status);
        taskinfoLogsMapper.updateById(taskinfoLogs);

        Task task = new Task();
        BeanUtils.copyProperties(taskinfoLogs, task);
        task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
        return task;
    }


    @Override
    public Task poll(int type, int priority) {
        String key = type + "_" + priority;
        String task_json = cacheService.lRightPop(ScheduleConstants.TOPIC + key);
        Task task = null;
        if (StringUtils.isNotBlank(task_json)) {
            task = JSON.parseObject(task_json, Task.class);
            updateDb(task.getTaskId(), ScheduleConstants.EXECUTED);
        }
        return task;
    }

    // 同步未来任务到立刻处理
    @Scheduled(cron = "0 */1 * * * ?")
    public void refresh() {
        // 尝试获取锁，只有一个线程能够执行，锁会在30秒后自动释放
        String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);
        if (StringUtils.isNotBlank(token)) {
            // 获取所有的future key
            Set<String> keys = cacheService.scan(ScheduleConstants.FUTURE + "*");
            // 遍历所有的key
            for (String key : keys) {
                // 对每个key提取到期的任务
                Set<String> tasks = cacheService.zRangeByScore(key, 0, System.currentTimeMillis());
                // 加入到立刻处理list集合中
                String topicKey = ScheduleConstants.TOPIC + key.substring(ScheduleConstants.FUTURE.length());
                if (!tasks.isEmpty()) {
                    cacheService.refreshWithPipeline(key, topicKey, tasks);
                }
            }
        }
    }

    // 同步数据库任务到redis
    @PostConstruct
    @Scheduled(cron = "0 */5 * * * ?")
    public void reloadData() {
        // 清理缓存
        clearCache();
        // 查询数据库内小于未来5分钟的任务
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        List<Taskinfo> taskinfos = taskinfoMapper.selectList(Wrappers.<Taskinfo>lambdaQuery().lt(Taskinfo::getExecuteTime, calendar.getTime()));
        // 将任务加入到缓存
        if (taskinfos != null && !taskinfos.isEmpty()) {
            for (Taskinfo taskinfo : taskinfos) {
                Task task = new Task();
                BeanUtils.copyProperties(taskinfo, task);
                task.setExecuteTime(taskinfo.getExecuteTime().getTime());
                addTaskToCache(task);
            }
        }
    }

    public void clearCache() {
        Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
        Set<String> topicKeys = cacheService.scan(ScheduleConstants.TOPIC + "*");
        cacheService.delete(futureKeys);
        cacheService.delete(topicKeys);
    }
}
