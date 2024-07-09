package com.heima.utils.thread;

import com.heima.model.wemedia.pojos.WmUser;

public class WmThreadLocalUtil {
    private final static ThreadLocal<WmUser> wmThreadLocal = new ThreadLocal<>();

    // 设置当前线程的用户信息
    public static void setUser(WmUser wmUser) {
        wmThreadLocal.set(wmUser);
    }

    public static WmUser getUser() {
        return wmThreadLocal.get();
    }

    public static void removeUser() {
        wmThreadLocal.remove();
    }
}
