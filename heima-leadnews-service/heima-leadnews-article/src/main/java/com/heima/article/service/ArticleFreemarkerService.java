package com.heima.article.service;

import com.heima.model.article.pojos.ApArticle;

public interface ArticleFreemarkerService {

    void buildArticleMinIo(ApArticle apArticle, String content) throws InterruptedException;
}
