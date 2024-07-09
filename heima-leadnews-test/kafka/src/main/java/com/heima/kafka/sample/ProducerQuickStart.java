package com.heima.kafka.sample;

import org.apache.kafka.clients.producer.*;

import java.util.Properties;

public class ProducerQuickStart {
    public static void main(String[] args) {
        // kafka连接配置
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // 设置消息确认机制， 0：不等待确认，1：leader确认，all：leader和所有follower确认
        props.put(ProducerConfig.RETRIES_CONFIG, 3); // 设置消息发送重试次数
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // 设置消息压缩方式
        // 创建生产者对象
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        // 发送消息
        ProducerRecord<String, String> record = new ProducerRecord<>("topic01", "key01", "hello kafka!"); // 设置了自动创建主题，所以不需要提前创建
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                exception.printStackTrace();
            } else {
                System.out.println("Message sent to topic " + metadata.topic() + " partition " + metadata.partition() + " with offset " + metadata.offset());
            }
        });        // 关闭资源
        producer.close();
    }
}
