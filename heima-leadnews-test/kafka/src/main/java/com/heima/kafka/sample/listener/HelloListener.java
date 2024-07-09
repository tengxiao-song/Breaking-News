package com.heima.kafka.sample.listener;

import com.alibaba.fastjson.JSON;
import com.heima.kafka.sample.pojo.User;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class HelloListener {

    @KafkaListener(topics = "topic01")
    public void listen(String message) {
        if (!StringUtils.isEmpty(message)) {
            User user = JSON.parseObject(message, User.class);
            System.out.println(user);
//            System.out.println("message = " + message);
        }
    }
}
