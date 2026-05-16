package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.CommentRequest;
import org.example.schoolshop.dto.req.CreatePostRequest;
import org.example.schoolshop.dto.vo.CommentVO;
import org.example.schoolshop.dto.vo.PostItemVO;
import org.example.schoolshop.service.PostService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public Result<PageResult<PostItemVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId) {
        return Result.ok(postService.list(page, pageSize, keyword, categoryId, UserContext.getUserId()));
    }

    @GetMapping("/{id}")
    public Result<PostItemVO> detail(@PathVariable Long id) {
        return Result.ok(postService.detail(id, UserContext.getUserId()));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody CreatePostRequest request) {
        return Result.ok(postService.create(UserContext.requireUserId(), request));
    }

    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        return Result.ok(postService.toggleLike(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/comments")
    public Result<CommentVO> comment(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        return Result.ok(postService.addComment(UserContext.requireUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        postService.deletePost(UserContext.requireUserId(), id);
        return Result.ok(null);
    }
}
