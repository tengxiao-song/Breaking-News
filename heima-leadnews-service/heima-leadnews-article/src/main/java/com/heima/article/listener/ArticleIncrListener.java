package com.heima.article.listener;

import com.alibaba.fastjson.JSON;
import com.heima.article.service.ApArticleService;
import com.heima.common.constans.HotArticleConstants;
import com.heima.model.mess.ArticleVisitStreamMess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ArticleIncrListener {

    @Autowired
    private ApArticleService articleService;

    @KafkaListener(topics = HotArticleConstants.HOT_ARTICLE_INCR_HANDLE_TOPIC)
    public void onMessage(String message) {
        if (message != null) {
            ArticleVisitStreamMess articleVisitStreamMess = JSON.parseObject(message, ArticleVisitStreamMess.class);
            articleService.UpdateScore(articleVisitStreamMess);
        }
    }
}
