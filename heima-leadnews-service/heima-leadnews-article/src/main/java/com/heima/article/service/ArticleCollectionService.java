package com.heima.article.service;

import com.heima.model.article.dtos.CollectionBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;
import org.springframework.web.bind.annotation.RequestBody;

public interface ArticleCollectionService {

    ResponseResult collect(CollectionBehaviorDto dto);
}
