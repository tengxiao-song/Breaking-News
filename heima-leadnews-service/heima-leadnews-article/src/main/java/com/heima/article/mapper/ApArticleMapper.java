package com.heima.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import org.apache.ibatis.annotations.Mapper;

import java.util.Date;
import java.util.List;

@Mapper
public interface ApArticleMapper extends BaseMapper<ApArticle> {
    // type 1 加载更多 2 加载最新
    public List<ApArticle> loadArticleList(ArticleHomeDto dto, Short type);

    public List<ApArticle> findArticleListByLastFiveDays(Date date);
}
