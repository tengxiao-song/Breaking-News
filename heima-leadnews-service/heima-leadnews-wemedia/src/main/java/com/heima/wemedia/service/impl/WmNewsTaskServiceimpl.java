package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.apis.schedule.IScheduleClient;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.TaskTypeEnum;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.utils.common.JdkSerializeUtil;
import com.heima.utils.common.ProtostuffUtil;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@Slf4j
public class WmNewsTaskServiceimpl implements WmNewsTaskService {

    @Autowired
    private IScheduleClient iScheduleClient;

    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;

    @Override
    @Async
    public void addNewsToTask(Integer id, Date publishTime) {
        Task task = new Task();
        task.setExecuteTime(publishTime.getTime());
        task.setTaskType(TaskTypeEnum.NEWS_SCAN_TIME.getTaskType());
        task.setPriority(TaskTypeEnum.NEWS_SCAN_TIME.getPriority());
        WmNews wmNews = new WmNews();
        wmNews.setId(id);
        task.setParameters(JdkSerializeUtil.serialize(wmNews));
        iScheduleClient.addTask(task);
        log.info("添加文章到延时任务成功");
    }

    @Override
    @Scheduled(fixedRate = 1000)
    public void scanNewsByTask() {
        ResponseResult responseResult = iScheduleClient.poll(TaskTypeEnum.NEWS_SCAN_TIME.getTaskType(), TaskTypeEnum.NEWS_SCAN_TIME.getPriority());
        if (responseResult.getCode().equals(200) && responseResult.getData() != null) {
            String jsonString = JSON.toJSONString(responseResult.getData());
            Task task = JSON.parseObject(jsonString, Task.class);
            WmNews news = JdkSerializeUtil.deserialize(task.getParameters(), WmNews.class);
            wmNewsAutoScanService.autoScanWmNews(news.getId());
            log.info("消费任务");
        }
    }
}
