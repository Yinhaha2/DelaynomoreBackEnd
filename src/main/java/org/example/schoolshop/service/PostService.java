package org.example.schoolshop.service;

import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.dto.req.CommentRequest;
import org.example.schoolshop.dto.req.CreatePostRequest;
import org.example.schoolshop.dto.vo.CommentVO;
import org.example.schoolshop.dto.vo.PostItemVO;

import java.util.Map;

public interface PostService {

    PageResult<PostItemVO> list(Integer page, Integer pageSize, String keyword, Integer categoryId, Long currentUserId);

    PostItemVO detail(Long postId, Long currentUserId);

    Map<String, Object> create(long userId, CreatePostRequest request);

    Map<String, Object> toggleLike(long userId, long postId);

    CommentVO addComment(long userId, long postId, CommentRequest request);

    void deletePost(long userId, long postId);
}
