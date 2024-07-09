package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.apis.article.IArticleClient;
import com.heima.common.test4j.Tess4jClient;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmSensitive;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.common.SensitiveWordUtil;
import com.heima.wemedia.mapper.WmChannelMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmSensitiveMapper;
import com.heima.wemedia.mapper.WmUserMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class WmNewsAutoScanServiceImpl implements WmNewsAutoScanService {

    @Autowired
    private WmNewsMapper wmNewsMapper;

    @Autowired
    private IArticleClient articleClient;

    @Autowired
    private WmChannelMapper wmChannelMapper;

    @Autowired
    private WmUserMapper wmUserMapper;

    @Autowired
    private WmSensitiveMapper wmSensitiveMapper;

    @Autowired
    private Tess4jClient tess4jClient;

    @Autowired
    private FileStorageService fileStorageService;

    @Override
    @Async
    public void autoScanWmNews(Integer id) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // 查询文章
        WmNews wmNews = wmNewsMapper.selectById(id);
        if (wmNews.getStatus().equals(WmNews.Status.SUBMIT.getCode())) {
            // 提取文本和图片
            Map<String, Object> map = handleTextAndImages(wmNews);
            // 审核文本
            boolean isSensitive = handleSensitiveScan((String) map.get("content"), wmNews);
            if (isSensitive) {
                return;
            }
            boolean isTextScan = handleTextScan((String) map.get("content"), wmNews);
            if (!isTextScan) {
                return;
            }
            // 审核图片
//            boolean isImageScan = handleImageScan((List<String>) map.get("images"), wmNews);
            // 审核成功后，保存app端文章数据
            ResponseResult responseResult = saveAppArticle(wmNews);
            if (responseResult.getCode().equals(200)) {
                wmNews.setArticleId((Long) responseResult.getData());
                wmNews.setStatus(WmNews.Status.PUBLISHED.getCode());
                wmNewsMapper.updateById(wmNews);
            }
        }
    }

    private boolean handleSensitiveScan(String content, WmNews wmNews) {
        boolean flag = false;
        List<WmSensitive> wmSensitives = wmSensitiveMapper.selectList(Wrappers.<WmSensitive>lambdaQuery().select(WmSensitive::getSensitives));
        List<String> sensitiveList = wmSensitives.stream().map(WmSensitive::getSensitives).collect(Collectors.toList());
        SensitiveWordUtil.initMap(sensitiveList);
        Map<String, Integer> map = SensitiveWordUtil.matchWords(content);
        if (map.size() > 0) {
            wmNews.setStatus(WmNews.Status.FAIL.getCode());
            wmNews.setReason("文章中包含敏感词汇");
            wmNewsMapper.updateById(wmNews);
            flag = true;
        }
        return flag;
    }

    private ResponseResult saveAppArticle(WmNews wmNews) {
        ArticleDto dto = new ArticleDto();
        BeanUtils.copyProperties(wmNews, dto);
        dto.setLayout(wmNews.getType());
        WmChannel wmChannel = wmChannelMapper.selectById(wmNews.getChannelId());
        dto.setChannelName(wmChannel.getName());
        dto.setAuthorId(Long.valueOf(wmNews.getUserId()));
        WmUser wmUser = wmUserMapper.selectById(wmNews.getUserId());
        dto.setAuthorName(wmUser.getName());
        if (wmNews.getArticleId() != null) {
            dto.setId(wmNews.getArticleId());
        }
        dto.setCreatedTime(new Date());
        ResponseResult responseResult = articleClient.saveArticle(dto);
        return responseResult;
    }

    private boolean handleImageScan(List<String> images, WmNews wmNews) {
        boolean flag = true;

        if (images == null || images.size() == 0) {
            return flag;
        }
        images = images.stream().distinct().collect(Collectors.toList());
        List<Byte[]> imageList = new ArrayList<>();
        for (String image : images) {
            byte[] bytes = fileStorageService.downLoadFile(image);
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            BufferedImage img = null;
            try {
                img = ImageIO.read(byteArrayInputStream);
                String res = tess4jClient.doOCR(img);
                handleSensitiveScan(res, wmNews);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    private boolean handleTextScan(String content, WmNews wmNews) {
        return true;
    }

    private Map<String, Object> handleTextAndImages(WmNews wmNews) {
        // 存储文本
        StringBuilder sb = new StringBuilder();

        // 存储图片
        List<String> images = new ArrayList<>();

        if (StringUtils.isNotBlank(wmNews.getContent())) {
            List<Map> maps = JSONArray.parseArray(wmNews.getContent(), Map.class);
            for (Map map : maps) {
                // 提取文本
                if (map.get("type").equals("text")) {
                    sb.append(map.get("value"));
                }
                if (map.get("type").equals("image")) {
                    images.add(map.get("value").toString());
                }
            }
        }
        if (StringUtils.isNotBlank(wmNews.getImages())) {
            String[] split = wmNews.getImages().split(",");
            images.addAll(Arrays.asList(split));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("content", sb.toString());
        res.put("images", images);
        return res;
    }
}
