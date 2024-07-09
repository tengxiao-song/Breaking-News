package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constans.ArticleConstants;
import com.heima.common.constans.BehaviorConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.dtos.ArticleInfoDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.article.vos.HotArticleVo;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.mess.ArticleVisitStreamMess;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService {

    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Autowired
    private ArticleFreemarkerService articleFreemarkerService;

    private static final Short MAX_PAGE_SIZE = 50;

    @Autowired
    private CacheService cacheService;

    @Override
    public ResponseResult load(ArticleHomeDto dto, Short type) {
        // 分页条数参数校验
        Integer size = dto.getSize();
        if (size == null || size == 0) {
            size = 10;
        }
        size = Math.min(size, MAX_PAGE_SIZE); // 限制最大值
        dto.setSize(size);
        // 参数校验
        if (!type.equals(ArticleConstants.LOADTYPE_LOAD_MORE) && !type.equals(ArticleConstants.LOADTYPE_LOAD_NEW)) {
            type = ArticleConstants.LOADTYPE_LOAD_MORE;
        }
        // 标签校验
        if (StringUtils.isBlank(dto.getTag())) {
            dto.setTag(ArticleConstants.DEFAULT_TAG);
        }
        // 时间校验
        if (dto.getMaxBehotTime() == null) {
            dto.setMaxBehotTime(new Date());
        }
        if (dto.getMinBehotTime() == null) {
            dto.setMinBehotTime(new Date());
        }

        List<ApArticle> apArticles = apArticleMapper.loadArticleList(dto, type);
        return ResponseResult.okResult(apArticles);
    }

    @Override
    public ResponseResult saveArticle(ArticleDto dto) {
        // 检查参数
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        // 如果id不存在，保存文章，文章配置，文章内容
        ApArticle apArticle = new ApArticle();
        BeanUtils.copyProperties(dto, apArticle);
        if (dto.getId() == null) {
            // 保存文章
            save(apArticle); // mp会自动将id回填到apArticle中
            // 保存文章配置
            ApArticleConfig apArticleConfig = new ApArticleConfig(apArticle.getId());
            apArticleConfigMapper.insert(apArticleConfig);
            // 保存文章内容
            ApArticleContent apArticleContent = new ApArticleContent();
            apArticleContent.setArticleId(apArticle.getId());
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.insert(apArticleContent);
        } else {
            // 如果存在，修改文章和文章内容
            updateById(apArticle);
            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers.<ApArticleContent>lambdaQuery().eq(ApArticleContent::getArticleId, dto.getId()));
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.updateById(apArticleContent);
        }

        // 异步调用，上传到minio
        try {
            articleFreemarkerService.buildArticleMinIo(apArticle, dto.getContent());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return ResponseResult.okResult(apArticle.getId());
    }

    @Override
    public ResponseResult load_article_behavior(ArticleInfoDto dto) {
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApUser user = AppThreadLocalUtil.getUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }

        Map<String, Boolean> map = new HashMap<>();
        Object data = cacheService.hGet(BehaviorConstants.LIKE_BEHAVIOR + user.getId(), dto.getArticleId().toString());
        if (data != null) {
            map.put("islike", true);
        } else {
            map.put("islike", false);
        }

        data = cacheService.hGet(BehaviorConstants.UN_LIKE_BEHAVIOR + user.getId(), dto.getArticleId().toString());
        if (data != null) {
            map.put("isunlike", true);
        } else {
            map.put("isunlike", false);
        }

        data = cacheService.hGet(BehaviorConstants.COLLECTION_BEHAVIOR + user.getId(), dto.getArticleId().toString());
        if (data != null) {
            map.put("iscollection", true);
        } else {
            map.put("iscollection", false);
        }

        data = cacheService.zScore(BehaviorConstants.APUSER_FOLLOW_RELATION + user.getId(), dto.getAuthorId().toString());
        if (data != null) {
            map.put("isfollow", true);
        } else {
            map.put("isfollow", false);
        }
        return ResponseResult.okResult(map);
    }

    @Override
    public ResponseResult load3(ArticleHomeDto dto, Short type, boolean firstPage) {
        if (firstPage) {
            // 从缓存中获取数据
            String hotArticleJson = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + dto.getTag());
            if (StringUtils.isNotBlank(hotArticleJson)) {
                List<HotArticleVo> apArticles = JSON.parseArray(hotArticleJson, HotArticleVo.class);
                return ResponseResult.okResult(apArticles);
            }
        }
        // 从数据库中获取数据
        return load(dto, type);
    }

    @Override
    public void UpdateScore(ArticleVisitStreamMess mess) {
        // 更新文章的数据
        ApArticle article = getById(mess.getArticleId());
        article.setCollection(article.getCollection()==null?0:article.getCollection() + mess.getCollect());
        article.setLikes(article.getLikes()==null?0:article.getLikes() + mess.getLike());
        article.setComment(article.getComment()==null?0:article.getComment() + mess.getComment());
        article.setViews(article.getViews()==null?0:article.getViews() + mess.getView());
        updateById(article);
        // 计算热度
        Integer score = computeScore(article);
        score = score * 3;
        // 可能替换低热度的文章
        String articleStr = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + article.getChannelId());
        if (StringUtils.isNotBlank(articleStr)) {
            List<HotArticleVo> hotArticleVos = JSON.parseArray(articleStr, HotArticleVo.class);
            // 已存在就更新
            boolean flag = true;
            for (HotArticleVo hotArticleVo : hotArticleVos) {
                if (hotArticleVo.getId().equals(article.getId())) {
                    hotArticleVo.setScore(score);
                    flag = false;
                    break;
                }
            }
            // 不存在且数量大于30，替换最小的
            if (flag && hotArticleVos.size() >= 30) {
                hotArticleVos = hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
                HotArticleVo lastHot = hotArticleVos.get(hotArticleVos.size() - 1);
                if (lastHot.getScore() < score) {
                    hotArticleVos.remove(lastHot);
                    HotArticleVo hotArticleVo = new HotArticleVo();
                    BeanUtils.copyProperties(article, hotArticleVo);
                    hotArticleVo.setScore(score);
                    hotArticleVos.add(hotArticleVo);
                }
            } else if (flag) {
                HotArticleVo hotArticleVo = new HotArticleVo();
                BeanUtils.copyProperties(article, hotArticleVo);
                hotArticleVo.setScore(score);
                hotArticleVos.add(hotArticleVo);
            }
            hotArticleVos = hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
            cacheService.set(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + article.getChannelId(), JSON.toJSONString(hotArticleVos));
        }


        String articleAllStr = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG);
        if (StringUtils.isNotBlank(articleStr)) {
            List<HotArticleVo> hotArticleVos = JSON.parseArray(articleStr, HotArticleVo.class);
            boolean flag = true;
            for (HotArticleVo hotArticleVo : hotArticleVos) {
                if (hotArticleVo.getId().equals(article.getId())) {
                    hotArticleVo.setScore(score);
                    flag = false;
                    break;
                }
            }
            if (flag && hotArticleVos.size() >= 30) {
                hotArticleVos = hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
                HotArticleVo lastHot = hotArticleVos.get(hotArticleVos.size() - 1);
                if (lastHot.getScore() < score) {
                    hotArticleVos.remove(lastHot);
                    HotArticleVo hotArticleVo = new HotArticleVo();
                    BeanUtils.copyProperties(article, hotArticleVo);
                    hotArticleVo.setScore(score);
                    hotArticleVos.add(hotArticleVo);
                }
            } else if (flag) {
                HotArticleVo hotArticleVo = new HotArticleVo();
                BeanUtils.copyProperties(article, hotArticleVo);
                hotArticleVo.setScore(score);
                hotArticleVos.add(hotArticleVo);
            }
            hotArticleVos = hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
            cacheService.set(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG, JSON.toJSONString(hotArticleVos));
        }
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
