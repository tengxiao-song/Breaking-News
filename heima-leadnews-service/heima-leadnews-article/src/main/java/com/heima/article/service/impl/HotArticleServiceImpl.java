package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.heima.apis.wemedia.IWemediaClient;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.HotArticleService;
import com.heima.common.constans.ArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.vos.HotArticleVo;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.nntp.Article;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HotArticleServiceImpl implements HotArticleService {

    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private IWemediaClient iWemediaClient;

    @Autowired
    private CacheService cacheService;

    @Override
    public void computeHotArticle() {
        // 查询最近五天的文章
        Date date = DateTime.now().minusDays(5).toDate();
        List<ApArticle> articleListByLastFiveDays = apArticleMapper.findArticleListByLastFiveDays(date);

        // 计算热度值
        List<HotArticleVo> hotArticleVoList = computeHotArticle(articleListByLastFiveDays);

        // 缓存每个频道的热度文章
        cacheTagToRedis(hotArticleVoList);
    }

    private void cacheTagToRedis(List<HotArticleVo> hotArticleVoList) {
        ResponseResult response = iWemediaClient.getChannels();
        if (response.getCode().equals(200)) {
            String jsonString = JSON.toJSONString(response.getData());
            List<WmChannel> channels = JSON.parseArray(jsonString, WmChannel.class);
            for (WmChannel wmChannel : channels) {
                List<HotArticleVo> hotArticleVos = hotArticleVoList.stream().filter(x -> x.getChannelId().equals(wmChannel.getId())).collect(Collectors.toList());
                if (hotArticleVos == null) {
                    continue;
                }
                hotArticleVos = hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
                if (hotArticleVos.size() > 30) {
                    hotArticleVos = hotArticleVos.subList(0, 30);
                }
                cacheService.set(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + wmChannel.getId(), JSON.toJSONString(hotArticleVos));
            }
        }

        hotArticleVoList = hotArticleVoList.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
        if (hotArticleVoList.size() > 30) {
            hotArticleVoList = hotArticleVoList.subList(0, 30);
        }
        cacheService.set(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG, JSON.toJSONString(hotArticleVoList));
    }

    private List<HotArticleVo> computeHotArticle(List<ApArticle> articleListByLastFiveDays) {
        List<HotArticleVo> hotArticleVoList = new ArrayList<>();
        if (articleListByLastFiveDays != null && articleListByLastFiveDays.size() > 0) {
            for (ApArticle apArticle : articleListByLastFiveDays) {
                HotArticleVo hotArticleVo = new HotArticleVo();
                BeanUtils.copyProperties(apArticle, hotArticleVo);
                Integer score = computeScore(apArticle);
                hotArticleVo.setScore(score);
                hotArticleVoList.add(hotArticleVo);
            }
        }
        return hotArticleVoList;
    }

    private Integer computeScore(ApArticle apArticle) {
        Integer score = 0;
        if (apArticle.getLikes() != null) {
            score += apArticle.getLikes() * ArticleConstants.HOT_ARTICLE_LIKE_WEIGHT;
        }
        if (apArticle.getViews() != null) {
            score += apArticle.getViews();
        }
        if (apArticle.getComment() != null) {
            score += apArticle.getComment() * ArticleConstants.HOT_ARTICLE_COMMENT_WEIGHT;
        }
        if (apArticle.getCollection() != null) {
            score += apArticle.getCollection() * ArticleConstants.HOT_ARTICLE_COLLECTION_WEIGHT;
        }
        return score;
    }
}
