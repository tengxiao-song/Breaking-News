package com.heima.utils.thread;

import com.heima.model.user.pojos.ApUser;
import com.heima.model.wemedia.pojos.WmUser;

public class AppThreadLocalUtil {
    private final static ThreadLocal<ApUser> wmThreadLocal = new ThreadLocal<>();

    // 设置当前线程的用户信息
    public static void setUser(ApUser apUser) {
        wmThreadLocal.set(apUser);
    }

    public static ApUser getUser() {
        return wmThreadLocal.get();
    }

    public static void removeUser() {
        wmThreadLocal.remove();
    }
}
