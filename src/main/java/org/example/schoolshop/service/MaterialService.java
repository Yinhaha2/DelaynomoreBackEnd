package org.example.schoolshop.service;

import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.dto.req.PublishMaterialRequest;
import org.example.schoolshop.dto.vo.MaterialItemVO;
import org.example.schoolshop.dto.vo.PayParamsVO;

import java.util.List;
import java.util.Map;

public interface MaterialService {

    PageResult<MaterialItemVO> list(Integer page, Integer pageSize, String sortBy, String category,
                                    String keyword, Long currentUserId);

    MaterialItemVO detail(Long materialId, Long currentUserId);

    Map<String, Object> publish(long userId, PublishMaterialRequest request);

    Map<String, Object> purchase(long userId, long materialId);

    Map<String, Object> downloadUrl(long userId, long materialId);

    Map<String, List<MaterialItemVO>> showcase(long userId);
}
