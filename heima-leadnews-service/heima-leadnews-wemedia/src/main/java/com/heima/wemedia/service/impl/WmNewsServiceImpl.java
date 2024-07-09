package com.heima.wemedia.service.impl;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constans.WemediaConstants;
import com.heima.common.constans.WmNewsMessageConstants;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsService;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsServiceImpl  extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;

    @Autowired
    private WmMaterialMapper wmMaterialMapper;

    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;

    @Autowired
    private WmNewsTaskService wmNewsTaskService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public ResponseResult findNews(WmNewsPageReqDto dto) {
        // 1. 参数检查
        dto.checkParam();
        // 2. 分页查询
        IPage thePage = new Page(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<WmNews> queryWrapper = new LambdaQueryWrapper();
        if (dto.getStatus() != null) {
            queryWrapper.eq(WmNews::getStatus, dto.getStatus());
        }
        if (dto.getChannelId() != null) {
            queryWrapper.eq(WmNews::getChannelId, dto.getChannelId());
        }
        if (dto.getBeginPubDate() != null && dto.getEndPubDate() != null) {
            queryWrapper.between(WmNews::getPublishTime, dto.getBeginPubDate(), dto.getEndPubDate());
        }
        if (StringUtils.isNotBlank(dto.getKeyword())) {
            queryWrapper.like(WmNews::getTitle, dto.getKeyword());
        }
        queryWrapper.eq(WmNews::getUserId, WmThreadLocalUtil.getUser().getId());
        queryWrapper.orderByDesc(WmNews::getCreatedTime);

        thePage = page(thePage, queryWrapper);
        ResponseResult responseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) thePage.getTotal());
        responseResult.setData(thePage.getRecords());
        // 3. 返回结果
        return responseResult;
    }

    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        if (dto == null || dto.getContent() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        // 保存或修改文章
        WmNews wmNews = new WmNews();
        BeanUtils.copyProperties(dto, wmNews);
        if (dto.getImages() != null && dto.getImages().size() > 0) {
            String imageStr = StringUtils.join(dto.getImages(), ",");
            wmNews.setImages(imageStr);
        }
        if (dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)) {
            wmNews.setType(null);
        }
        saveOrUpdateWmNews(wmNews);
        // 判断是否为草稿
        if (dto.getStatus().equals(WmNews.Status.NORMAL.getCode())) {
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }
        // 不是草稿，保存文章内容与素材关联关系
        List<String> materials = extractUrlInfo(dto.getContent());
        saveRelativeInfoForContent(materials, wmNews.getId());
        // 保存文章封面与素材关联关系
        saveRelativeInfoForCover(dto, wmNews, materials);
        // 自动审核
//        wmNewsAutoScanService.autoScanWmNews(wmNews.getId()); // 异步调用
        // 添加到延时任务
        wmNewsTaskService.addNewsToTask(wmNews.getId(), wmNews.getPublishTime());
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    private void saveRelativeInfoForCover(WmNewsDto dto, WmNews wmNews, List<String> materials) {
        // 如果封面类型为自动，则设置封面类型的数据
        // 如果内容图片>1 <=3，单图type=1， 如果>3，多图type=3，如果没有，无图type=0
        List<String> images = dto.getImages();
        if (dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)) {
            if (materials.size() >= 3) {
                wmNews.setType(WemediaConstants.WM_NEWS_MANY_IMAGE);
                images = materials.stream().limit(3).collect(Collectors.toList());
            } else if (materials.size() > 0) {
                wmNews.setType(WemediaConstants.WM_NEWS_SINGLE_IMAGE);
                images = materials.stream().limit(1).collect(Collectors.toList());
            } else {
                wmNews.setType(WemediaConstants.WM_NEWS_NONE_IMAGE);
            }
            if (images != null && images.size() > 0) {
                String imageStr = StringUtils.join(images, ",");
                wmNews.setImages(imageStr);
            }
            updateById(wmNews);
        }
        // 保存文章封面与素材关联关系
        saveRelativeInfo(images, wmNews.getId(), WemediaConstants.WM_COVER_REFERENCE);
    }

    private void saveRelativeInfo(List<String> materials, Integer id, Short type) {
        // 保存文章内容与素材关联关系
        if (materials != null && materials.size() > 0) {
            List<WmMaterial> wmMaterials = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, materials));
            List<Integer> idList = wmMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());
            wmNewsMaterialMapper.saveRelations(idList, id, type);
        }
    }

    private void saveRelativeInfoForContent(List<String> materials, Integer id) {
        // 保存文章内容与素材关联关系
        saveRelativeInfo(materials, id, WemediaConstants.WM_CONTENT_REFERENCE);
    }

    private List<String> extractUrlInfo(String content) {
        // 从content中提取所有图片url
        List<String> materials = new ArrayList<>();
        List<Map> maps = JSON.parseArray(content, Map.class);
        for (Map map : maps) {
            if (map.get("type").equals("image")) {
                String imgUrl = (String) map.get("value");
                materials.add(imgUrl);
            }
        }
        return materials;
    }

    private void saveOrUpdateWmNews(WmNews wmNews) {
        wmNews.setUserId(WmThreadLocalUtil.getUser().getId());
        wmNews.setCreatedTime(new Date());
        wmNews.setSubmitedTime(new Date());
        wmNews.setEnable((short) 1); // 默认上架

        if (wmNews.getId() == null) {
            save(wmNews);
        } else {
            wmNewsMaterialMapper.delete(Wrappers.<WmNewsMaterial>lambdaQuery().eq(WmNewsMaterial::getNewsId, wmNews.getId()));
            updateById(wmNews);
        }
    }

    @Override
    public ResponseResult findWmNewsById(Integer id) {
        WmNews item = getById(id);
        if (item == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }
        return ResponseResult.okResult(item);
    }

    @Override
    public ResponseResult delNews(Integer id) {
        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        WmNews wmNews = getById(id);
        if (wmNews == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }
        if (wmNews.getStatus().equals(WmNews.Status.PUBLISHED.getCode())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID, "已发布的文章不能删除");
        }
        removeById(id);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public ResponseResult downOrUp(WmNewsDto dto) {
        // 检查参数
        if (dto.getId() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        // 查询文章
        WmNews item = getById(dto.getId());
        if (item == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }
        // 判断文章是否已发布
        if (!item.getStatus().equals(WmNews.Status.PUBLISHED.getCode())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID, "只有发布的文章才能上下架");
        }
        // 修改文章enable字段
        if (dto.getEnable() != null && dto.getEnable() > - 1 && dto.getEnable() < 2) {
            update(Wrappers.<WmNews>lambdaUpdate().eq(WmNews::getId, dto.getId()).set(WmNews::getEnable, dto.getEnable()));

            // 发送kafka消息,通知app端文章上下架
            Map<String, Object> map = new HashMap<>();
            map.put("articleId", item.getArticleId());
            map.put("enable", dto.getEnable());
            kafkaTemplate.send(WmNewsMessageConstants.WM_NEWS_UP_OR_DOWN_TOPIC, JSON.toJSONString(map));
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

}
