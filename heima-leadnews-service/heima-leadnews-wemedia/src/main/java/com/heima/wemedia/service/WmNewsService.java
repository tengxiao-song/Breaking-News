package com.heima.wemedia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmNews;
import org.springframework.web.bind.annotation.RequestBody;

public interface WmNewsService extends IService<WmNews> {

    ResponseResult findNews(WmNewsPageReqDto dto);

    ResponseResult submitNews(WmNewsDto dto);

    ResponseResult findWmNewsById(Integer id);

    ResponseResult delNews(Integer id);

    ResponseResult downOrUp(WmNewsDto dto);
}
