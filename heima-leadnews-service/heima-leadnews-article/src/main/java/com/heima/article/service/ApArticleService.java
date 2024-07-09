package com.heima.article.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.dtos.ArticleInfoDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.mess.ArticleVisitStreamMess;
import org.springframework.web.bind.annotation.RequestBody;

public interface ApArticleService extends IService<ApArticle> {

    ResponseResult load(ArticleHomeDto dto, Short type);

    ResponseResult saveArticle(ArticleDto dto);

    ResponseResult load_article_behavior(ArticleInfoDto dto);

    ResponseResult load3(ArticleHomeDto dto, Short type, boolean firstPage);

    void UpdateScore(ArticleVisitStreamMess articleVisitStreamMess);
}
