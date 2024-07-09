package com.heima.kafka.sample;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.ValueMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

public class KafkaStreamQuickStart {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-stream-quick-start");

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, String> stream = builder.stream("topic-input");
        stream.flatMapValues(new ValueMapper<String, Iterable<?>>() {
            @Override
            public Iterable<String> apply(String s) {
                String[] split = s.split(" ");
                return Arrays.asList(split);
            }
        }).groupBy((key, value) -> value).windowedBy(TimeWindows.of(Duration.ofSeconds(5)))
                .count().toStream().map((key, value) -> {
                    return new KeyValue<>(key.key().toString(),value.toString());
                }).to("topic-output");

        KafkaStreams streams = new KafkaStreams(builder.build(),props);

        streams.start();
    }
}
