package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.domain.Material;
import org.example.schoolshop.domain.TradeOrder;
import org.example.schoolshop.domain.UploadFile;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.domain.UserMaterial;
import org.example.schoolshop.dto.req.PublishMaterialRequest;
import org.example.schoolshop.dto.vo.MaterialItemVO;
import org.example.schoolshop.integration.oss.OssStorageService;
import org.example.schoolshop.mapper.*;
import org.example.schoolshop.service.ContentSecurityService;
import org.example.schoolshop.service.MaterialService;
import org.example.schoolshop.service.PointsService;
import org.example.schoolshop.service.UserService;
import org.example.schoolshop.util.VoAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private static final int PLATFORM_FEE_PERCENT = 5;

    private final MaterialMapper materialMapper;
    private final UserMaterialMapper userMaterialMapper;
    private final UploadFileMapper uploadFileMapper;
    private final TradeOrderMapper orderMapper;
    private final UserService userService;
    private final PointsService pointsService;
    private final ContentSecurityService contentSecurityService;
    private final OssStorageService ossStorageService;

    @Override
    public PageResult<MaterialItemVO> list(Integer page, Integer pageSize, String sortBy, String category,
                                           String keyword, Long currentUserId) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<Material>().eq(Material::getStatus, 1);
        if (StringUtils.hasText(category)) {
            qw.eq(Material::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(Material::getTitle, keyword);
        }
        if ("price".equals(sortBy)) {
            qw.orderByAsc(Material::getPrice);
        } else {
            qw.orderByDesc(Material::getCreatedAt);
        }
        Page<Material> pageData = materialMapper.selectPage(new Page<>(p, ps), qw);
        List<MaterialItemVO> list = pageData.getRecords().stream()
                .map(m -> toItem(m, currentUserId)).collect(Collectors.toList());
        PageResult<MaterialItemVO> result = new PageResult<>();
        result.setList(list);
        result.setTotal(pageData.getTotal());
        result.setPage(pageData.getCurrent());
        result.setPageSize(pageData.getSize());
        result.setHasMore(pageData.getCurrent() * pageData.getSize() < pageData.getTotal());
        return result;
    }

    @Override
    public MaterialItemVO detail(Long materialId, Long currentUserId) {
        Material m = getOnShelf(materialId);
        return toItem(m, currentUserId);
    }

    @Override
    @Transactional
    public Map<String, Object> publish(long userId, PublishMaterialRequest request) {
        userService.requireActiveUser(userId);
        contentSecurityService.checkText(request.getTitle());
        contentSecurityService.checkText(request.getDescription());
        long cnt = uploadFileMapper.selectCount(new LambdaQueryWrapper<UploadFile>()
                .eq(UploadFile::getUserId, userId)
                .eq(UploadFile::getFileKey, request.getFileKey()));
        if (cnt == 0) {
            throw BizException.badRequest("fileKey 无效");
        }
        Material m = new Material();
        m.setUserId(userId);
        m.setTitle(request.getTitle());
        m.setDescription(request.getDescription());
        m.setPrice(request.getPrice());
        m.setFileKey(request.getFileKey());
        m.setCoverUrl(request.getCoverUrl() != null ? request.getCoverUrl() : "");
        m.setCategory(StringUtils.hasText(request.getCategory()) ? request.getCategory() : "report");
        m.setStatus(0);
        m.setSoldCount(0);
        materialMapper.insert(m);
        Map<String, Object> data = new HashMap<>();
        data.put("id", m.getId());
        data.put("status", m.getStatus());
        data.put("message", "已提交审核");
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> purchase(long userId, long materialId) {
        userService.requireActiveUser(userId);
        Material m = getOnShelf(materialId);
        if (m.getUserId().equals(userId)) {
            throw BizException.unprocessable("不能购买自己的资料");
        }
        if (userMaterialMapper.selectCount(new LambdaQueryWrapper<UserMaterial>()
                .eq(UserMaterial::getUserId, userId).eq(UserMaterial::getMaterialId, materialId)) > 0) {
            throw BizException.unprocessable("已购买过该资料");
        }

        pointsService.deduct(userId, m.getPrice(), "兑换资料：" + m.getTitle(), "material", m.getId());
        pointsService.addIncome(m.getUserId(),
                m.getPrice() - m.getPrice() * PLATFORM_FEE_PERCENT / 100,
                "资料兑换收入：" + m.getTitle(), "material", m.getId());

        UserMaterial um = new UserMaterial();
        um.setUserId(userId);
        um.setMaterialId(materialId);
        userMaterialMapper.insert(um);

        m.setSoldCount(m.getSoldCount() + 1);
        materialMapper.updateById(m);

        TradeOrder order = new TradeOrder();
        order.setOrderNo("O" + System.currentTimeMillis());
        order.setUserId(userId);
        order.setType("material");
        order.setBizId(materialId);
        order.setTitle(m.getTitle());
        order.setAmount(m.getPrice());
        order.setStatus(1);
        order.setOutTradeNo("MAT_" + materialId + "_" + UUID.randomUUID().toString().substring(0, 8));
        order.setPaidAt(LocalDateTime.now());
        orderMapper.insert(order);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("success", true);
        data.put("balance", pointsService.availableBalance(userId));
        Map<String, Object> payParams = new HashMap<>();
        payParams.put("mock", true);
        data.put("payParams", payParams);
        return data;
    }

    @Override
    public Map<String, Object> downloadUrl(long userId, long materialId) {
        userService.requireActiveUser(userId);
        Material m = materialMapper.selectById(materialId);
        if (m == null) {
            throw BizException.notFound("资料不存在");
        }
        boolean owned = userMaterialMapper.selectCount(new LambdaQueryWrapper<UserMaterial>()
                .eq(UserMaterial::getUserId, userId).eq(UserMaterial::getMaterialId, materialId)) > 0;
        if (!owned && !m.getUserId().equals(userId)) {
            throw BizException.forbidden("请先购买");
        }
        int expire = 600;
        String url;
        if (ossStorageService.isConfigured()) {
            url = ossStorageService.presignedPrivateUrl(m.getFileKey(), expire);
        } else {
            url = "https://private-bucket.mock/" + m.getFileKey() + "?Expires=" + expire;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("expiresIn", expire);
        return data;
    }

    @Override
    public Map<String, List<MaterialItemVO>> showcase(long userId) {
        userService.requireActiveUser(userId);
        List<Material> list = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .eq(Material::getUserId, userId).orderByDesc(Material::getCreatedAt));
        Map<String, List<MaterialItemVO>> data = new HashMap<>();
        data.put("list", list.stream().map(m -> toItem(m, userId)).collect(Collectors.toList()));
        return data;
    }

    private Material getOnShelf(Long id) {
        Material m = materialMapper.selectById(id);
        if (m == null || m.getStatus() != 1) {
            throw BizException.notFound("资料不存在");
        }
        return m;
    }

    private MaterialItemVO toItem(Material m, Long currentUserId) {
        MaterialItemVO vo = VoAssembler.toMaterialItem(m);
        vo.setOwned(false);
        if (currentUserId != null) {
            boolean owned = userMaterialMapper.selectCount(new LambdaQueryWrapper<UserMaterial>()
                    .eq(UserMaterial::getUserId, currentUserId)
                    .eq(UserMaterial::getMaterialId, m.getId())) > 0;
            vo.setOwned(owned || m.getUserId().equals(currentUserId));
        }
        return vo;
    }
}
