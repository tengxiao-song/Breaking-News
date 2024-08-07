package com.heima.article.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.service.ApArticleConfigService;
import com.heima.model.article.pojos.ApArticleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class ApArticleConfigImpl extends ServiceImpl<ApArticleConfigMapper, ApArticleConfig> implements ApArticleConfigService {

    @Override
    public void updateByMap(Map map) {
        Object id = map.get("articleId");
        Object enable = map.get("enable");
        boolean isDown = true;
        if (enable.equals(1)) {
            isDown = false;
        }
        update(Wrappers.<ApArticleConfig>lambdaUpdate()
                                .eq(ApArticleConfig::getArticleId, id)
                                .set(ApArticleConfig::getIsDown, isDown));
    }
}
