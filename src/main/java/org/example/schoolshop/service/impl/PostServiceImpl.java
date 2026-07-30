package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.domain.*;
import org.example.schoolshop.dto.req.CommentRequest;
import org.example.schoolshop.dto.req.CreatePostRequest;
import org.example.schoolshop.dto.vo.CommentVO;
import org.example.schoolshop.dto.vo.PostItemVO;
import org.example.schoolshop.mapper.*;
import org.example.schoolshop.service.ContentSecurityService;
import org.example.schoolshop.service.PostService;
import org.example.schoolshop.service.UserService;
import org.example.schoolshop.util.VoAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostCommentMapper postCommentMapper;
    private final UserMapper userMapper;
    private final ActivityNotificationMapper notificationMapper;
    private final UserService userService;
    private final ContentSecurityService contentSecurityService;

    @Override
    public PageResult<PostItemVO> list(Integer page, Integer pageSize, String keyword,
                                       Integer categoryId, Long currentUserId) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 1).eq(Post::getIsDeleted, 0)
                .orderByDesc(Post::getCreatedAt);
        if (categoryId != null) {
            qw.eq(Post::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(Post::getContent, keyword);
        }
        Page<Post> pageData = postMapper.selectPage(new Page<>(p, ps), qw);
        List<PostItemVO> list = pageData.getRecords().stream()
                .map(post -> toItem(post, currentUserId))
                .collect(Collectors.toList());
        PageResult<PostItemVO> result = PageResult.of(pageData.convert(r -> toItem(r, currentUserId)));
        result.setList(list);
        return result;
    }

    @Override
    public PostItemVO detail(Long postId, Long currentUserId) {
        Post post = getPublishedPost(postId);
        PostItemVO vo = toItem(post, currentUserId);
        List<PostComment> comments = postCommentMapper.selectList(new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getPostId, postId).orderByAsc(PostComment::getCreatedAt));
        vo.setComments(comments.stream().map(c -> {
            User u = userMapper.selectById(c.getUserId());
            CommentVO cv = new CommentVO();
            cv.setId(c.getId());
            cv.setUser(VoAssembler.toUserBrief(u));
            cv.setContent(c.getContent());
            cv.setCreatedAt(c.getCreatedAt());
            return cv;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional
    public Map<String, Object> create(long userId, CreatePostRequest request) {
        User user = userService.requireActiveUser(userId);
        if (!Boolean.TRUE.equals(user.getRealNameVerified())) {
            throw BizException.unprocessable("请先完成实名认证");
        }
        contentSecurityService.checkText(request.getContent());
        Post post = new Post();
        post.setUserId(userId);
        post.setCategoryId(request.getCategoryId());
        post.setContent(request.getContent());
        post.setImages(request.getImages());
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(1);
        post.setIsDeleted(0);
        postMapper.insert(post);
        Map<String, Object> data = new HashMap<>();
        data.put("id", post.getId());
        data.put("status", post.getStatus());
        data.put("message", "发布成功");
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> toggleLike(long userId, long postId) {
        userService.requireActiveUser(userId);
        Post post = getPublishedPost(postId);
        PostLike like = postLikeMapper.selectOne(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId).eq(PostLike::getPostId, postId));
        boolean liked;
        if (like != null) {
            postLikeMapper.deleteById(like.getId());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            liked = false;
        } else {
            PostLike nl = new PostLike();
            nl.setUserId(userId);
            nl.setPostId(postId);
            postLikeMapper.insert(nl);
            post.setLikeCount(post.getLikeCount() + 1);
            liked = true;
            if (!java.util.Objects.equals(userId, post.getUserId())) {
                ActivityNotification n = new ActivityNotification();
                n.setUserId(post.getUserId());
                n.setActorId(userId);
                n.setType("LIKE_POST");
                n.setPostId(postId);
                if (post.getImages() != null && !post.getImages().isEmpty()) {
                    n.setPostThumbnail(post.getImages().get(0));
                }
                n.setRead(0);
                notificationMapper.insert(n);
            }
        }
        postMapper.updateById(post);
        Map<String, Object> data = new HashMap<>();
        data.put("liked", liked);
        data.put("likeCount", post.getLikeCount());
        return data;
    }

    @Override
    @Transactional
    public CommentVO addComment(long userId, long postId, CommentRequest request) {
        userService.requireActiveUser(userId);
        Post post = getPublishedPost(postId);
        contentSecurityService.checkText(request.getContent());
        PostComment c = new PostComment();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setParentId(0L);
        c.setContent(request.getContent());
        postCommentMapper.insert(c);
        post.setCommentCount(post.getCommentCount() + 1);
        postMapper.updateById(post);
        if (!java.util.Objects.equals(userId, post.getUserId())) {
            ActivityNotification n = new ActivityNotification();
            n.setUserId(post.getUserId());
            n.setActorId(userId);
            n.setType("COMMENT_POST");
            n.setPostId(postId);
            n.setCommentText(request.getContent());
            if (post.getImages() != null && !post.getImages().isEmpty()) {
                n.setPostThumbnail(post.getImages().get(0));
            }
            n.setRead(0);
            notificationMapper.insert(n);
        }
        User u = userMapper.selectById(userId);
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setUser(VoAssembler.toUserBrief(u));
        vo.setContent(c.getContent());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }

    @Override
    @Transactional
    public void deletePost(long userId, long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || !java.util.Objects.equals(post.getUserId(), userId)) {
            throw BizException.forbidden("无权删除");
        }
        post.setIsDeleted(1);
        postMapper.updateById(post);
    }

    private Post getPublishedPost(long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getIsDeleted() == 1 || post.getStatus() != 1) {
            throw BizException.notFound("帖子不存在");
        }
        return post;
    }

    private PostItemVO toItem(Post post, Long currentUserId) {
        User author = userMapper.selectById(post.getUserId());
        boolean liked = false;
        if (currentUserId != null) {
            liked = postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                    .eq(PostLike::getUserId, currentUserId)
                    .eq(PostLike::getPostId, post.getId())) > 0;
        }
        return VoAssembler.toPostItem(post, author, liked);
    }
}
