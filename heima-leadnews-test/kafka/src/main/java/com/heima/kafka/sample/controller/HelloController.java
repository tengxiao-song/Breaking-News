package com.heima.kafka.sample.controller;

import com.alibaba.fastjson.JSON;
import com.heima.kafka.sample.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @GetMapping("/hello")
    public String hello() {
//        kafkaTemplate.send("topic01", "hello kafka!");
//        return "hello";
        User user = new User("zhangsan", 18);
        kafkaTemplate.send("topic01", JSON.toJSONString(user));
        return "hello";
    }
}
