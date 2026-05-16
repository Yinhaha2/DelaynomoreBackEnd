package org.example.schoolshop.util;

import org.example.schoolshop.common.CategoryConstants;
import org.example.schoolshop.common.OrderStatusUtil;
import org.example.schoolshop.domain.*;
import org.example.schoolshop.dto.vo.*;

import java.util.Collections;
import java.util.List;

public final class VoAssembler {

    private VoAssembler() {
    }

    public static UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setStudentId(user.getStudentId());
        vo.setRealNameVerified(Boolean.TRUE.equals(user.getRealNameVerified()));
        vo.setStatus(user.getStatus());
        vo.setWalletBalance(user.getWalletBalance());
        vo.setBio(user.getBio());
        return vo;
    }

    public static UserBriefVO toUserBrief(User user) {
        if (user == null) {
            return null;
        }
        UserBriefVO vo = new UserBriefVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return vo;
    }

    public static PostItemVO toPostItem(Post post, User author, boolean liked) {
        PostItemVO vo = new PostItemVO();
        vo.setId(post.getId());
        vo.setUserId(post.getUserId());
        vo.setUser(toUserBrief(author));
        vo.setCategoryId(post.getCategoryId());
        vo.setCategoryName(CategoryConstants.postCategoryName(post.getCategoryId()));
        vo.setContent(post.getContent());
        vo.setImages(post.getImages() != null ? post.getImages() : Collections.emptyList());
        vo.setLikeCount(post.getLikeCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setLiked(liked);
        vo.setStatus(post.getStatus());
        vo.setCreatedAt(post.getCreatedAt());
        return vo;
    }

    public static TaskItemVO toTaskItem(Task task, User publisher, User acceptor) {
        TaskItemVO vo = new TaskItemVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setLocation(task.getLocation());
        vo.setRewardAmount(task.getRewardAmount());
        vo.setStatus(task.getStatus());
        vo.setCategory(task.getCategory());
        vo.setTags(task.getTags());
        vo.setPublisher(toUserBrief(publisher));
        vo.setAcceptor(toUserBrief(acceptor));
        vo.setDeliveryNote(task.getDeliveryNote());
        vo.setDeliveryImages(task.getDeliveryImages());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setDeadline(task.getDeadline());
        return vo;
    }

    public static MaterialItemVO toMaterialItem(Material m) {
        MaterialItemVO vo = new MaterialItemVO();
        vo.setId(m.getId());
        vo.setTitle(m.getTitle());
        vo.setDescription(m.getDescription());
        vo.setPrice(m.getPrice());
        vo.setCoverUrl(m.getCoverUrl());
        vo.setFileType(m.getFileType());
        vo.setCategory(m.getCategory());
        vo.setSoldCount(m.getSoldCount());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }

    public static OrderItemVO toOrderItem(TradeOrder order) {
        OrderItemVO vo = new OrderItemVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setType(order.getType());
        vo.setTitle(order.getTitle());
        vo.setAmount(order.getAmount());
        vo.setStatus(OrderStatusUtil.toFrontend(order.getStatus()));
        vo.setCreatedAt(order.getCreatedAt());
        return vo;
    }
}
