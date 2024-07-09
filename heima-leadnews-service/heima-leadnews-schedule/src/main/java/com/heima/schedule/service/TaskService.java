package com.heima.schedule.service;

import com.heima.model.schedule.dtos.Task;

public interface TaskService {

    public long addTask(Task task);

    boolean cancelTask(Long taskId);

    Task poll(int type, int priority);
}
