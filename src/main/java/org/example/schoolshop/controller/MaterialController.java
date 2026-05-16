package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.PublishMaterialRequest;
import org.example.schoolshop.dto.vo.MaterialItemVO;
import org.example.schoolshop.service.MaterialService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    public Result<PageResult<MaterialItemVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(defaultValue = "time") String sortBy,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return Result.ok(materialService.list(page, pageSize, sortBy, category, keyword, UserContext.getUserId()));
    }

    @GetMapping("/{id}")
    public Result<MaterialItemVO> detail(@PathVariable Long id) {
        return Result.ok(materialService.detail(id, UserContext.getUserId()));
    }

    @PostMapping
    public Result<Map<String, Object>> publish(@Valid @RequestBody PublishMaterialRequest request) {
        return Result.ok(materialService.publish(UserContext.requireUserId(), request));
    }

    @PostMapping("/{id}/purchase")
    public Result<Map<String, Object>> purchase(@PathVariable Long id) {
        return Result.ok(materialService.purchase(UserContext.requireUserId(), id));
    }

    @GetMapping("/{id}/download-url")
    public Result<Map<String, Object>> downloadUrl(@PathVariable Long id) {
        return Result.ok(materialService.downloadUrl(UserContext.requireUserId(), id));
    }

    @GetMapping("/my/showcase")
    public Result<Map<String, List<MaterialItemVO>>> showcase() {
        return Result.ok(materialService.showcase(UserContext.requireUserId()));
    }
}
