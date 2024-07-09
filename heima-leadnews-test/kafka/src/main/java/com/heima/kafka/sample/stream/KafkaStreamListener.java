package com.heima.kafka.sample.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;

@Configuration
@Slf4j
public class KafkaStreamListener {

    @Bean
    public KStream<String, String> kStream(StreamsBuilder builder) {
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
        return stream;
    }
}
