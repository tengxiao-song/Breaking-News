package com.heima.article.stream;

import com.alibaba.fastjson.JSON;
import com.heima.common.constans.HotArticleConstants;
import com.heima.model.mess.ArticleVisitStreamMess;
import com.heima.model.mess.UpdateArticleMess;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class HotArticleSteamHandler {

    @Bean
    public KStream<String, String> hotArticleSteam(StreamsBuilder builder) {
        // 接收消息
        KStream<String, String> stream = builder.stream(HotArticleConstants.HOT_ARTICLE_SCORE_TOPIC);
        // 流处理 (value是UpdateArticleMess对象)
        stream.map((k, v) -> {
            UpdateArticleMess updateArticleMess = JSON.parseObject(v, UpdateArticleMess.class);
            // 重制消息的key,value key=文章id, val=类型:增加值
            return new KeyValue<>(updateArticleMess.getArticleId().toString() , updateArticleMess.getType().name() + ":" + updateArticleMess.getAdd());
        }).groupBy((k, v) -> k).windowedBy(TimeWindows.of(Duration.ofSeconds(10)))
                .aggregate(new Initializer<String>() {
                    @Override
                    public String apply() {
                        return "COLLECTION:0,COMMENT:0,LIKES:0,VIEWS:0";
                    }
                }, new Aggregator<String, String, String>() {
                    @Override // aggValue就是"COLLECTION:0,COMMENT:0,LIKES:0,VIEWS:0";
                    public String apply(String key, String val, String aggValue) {
                        // 初始化值
                        String[] split = aggValue.split(",");
                        int collection = 0, comment = 0, likes = 0, views = 0;
                        for (String s : split) {
                            String[] data = s.split(":");
                            switch (UpdateArticleMess.UpdateArticleType.valueOf(data[0])) {
                                case COLLECTION:
                                    collection = Integer.parseInt(data[1]);
                                    break;
                                case COMMENT:
                                    comment = Integer.parseInt(data[1]);
                                    break;
                                case LIKES:
                                    likes = Integer.parseInt(data[1]);
                                    break;
                                case VIEWS:
                                    views = Integer.parseInt(data[1]);
                                    break;
                            }
                        }
                        // 更新值
                        String[] split1 = val.split(":");
                        switch (UpdateArticleMess.UpdateArticleType.valueOf(split1[0])) {
                            case COLLECTION:
                                collection += Integer.parseInt(split1[1]);
                                break;
                            case COMMENT:
                                comment += Integer.parseInt(split1[1]);
                                break;
                            case LIKES:
                                likes += Integer.parseInt(split1[1]);
                                break;
                            case VIEWS:
                                views += Integer.parseInt(split1[1]);
                                break;
                        }
                        return "COLLECTION:" + collection + ",COMMENT:" + comment + ",LIKES:" + likes + ",VIEWS:" + views;
                    }
                }, Materialized.as("hot-article-stream-001")).toStream()
                        .map((k, v) -> new KeyValue<>(k.key(), formatObj(k.key() ,v)))
                        .to(HotArticleConstants.HOT_ARTICLE_INCR_HANDLE_TOPIC);
        // 发送消息
        return stream;
    }

    public String formatObj(String articleId, String value) {
        ArticleVisitStreamMess articleVisitStreamMess = new ArticleVisitStreamMess();
        articleVisitStreamMess.setArticleId(Long.valueOf(articleId));
        String[] split = value.split(",");
        for (String s : split) {
            String[] split1 = s.split(":");
            switch (UpdateArticleMess.UpdateArticleType.valueOf(split1[0])) {
                case COLLECTION:
                    articleVisitStreamMess.setCollect(Integer.parseInt(split1[1]));
                    break;
                case COMMENT:
                    articleVisitStreamMess.setComment(Integer.parseInt(split1[1]));
                    break;
                case LIKES:
                    articleVisitStreamMess.setLike(Integer.parseInt(split1[1]));
                    break;
                case VIEWS:
                    articleVisitStreamMess.setView(Integer.parseInt(split1[1]));
                    break;
            }
        }
        return JSON.toJSONString(articleVisitStreamMess);
    }
}
