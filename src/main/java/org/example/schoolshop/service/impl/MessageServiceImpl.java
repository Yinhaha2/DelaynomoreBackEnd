package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.domain.Conversation;
import org.example.schoolshop.domain.Message;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.dto.req.SendMessageRequest;
import org.example.schoolshop.dto.vo.ConversationVO;
import org.example.schoolshop.dto.vo.MessageVO;
import org.example.schoolshop.mapper.ConversationMapper;
import org.example.schoolshop.mapper.MessageMapper;
import org.example.schoolshop.mapper.UserMapper;
import org.example.schoolshop.service.MessageService;
import org.example.schoolshop.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final UserService userService;

    @Override
    public Map<String, List<ConversationVO>> conversations(long userId) {
        userService.requireActiveUser(userId);
        List<Conversation> list = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId).orderByDesc(Conversation::getUpdatedAt));
        List<ConversationVO> vos = list.stream().map(c -> {
            User peer = userMapper.selectById(c.getPeerId());
            ConversationVO vo = new ConversationVO();
            vo.setId(c.getId());
            vo.setPeerId(c.getPeerId());
            vo.setPeerName(peer != null ? peer.getNickname() : "");
            vo.setPeerAvatar(peer != null ? peer.getAvatar() : "");
            vo.setLastMessage(c.getLastMessage());
            vo.setLastTime(c.getLastTime());
            vo.setUnread(c.getUnread());
            return vo;
        }).collect(Collectors.toList());
        Map<String, List<ConversationVO>> data = new HashMap<>();
        data.put("list", vos);
        return data;
    }

    @Override
    public PageResult<MessageVO> chatMessages(long userId, long peerId, Integer page, Integer pageSize) {
        userService.requireActiveUser(userId);
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 30 : Math.min(pageSize, 50);
        Page<Message> pageData = messageMapper.selectPage(new Page<>(p, ps),
                new LambdaQueryWrapper<Message>()
                        .and(w -> w.nested(n -> n.eq(Message::getSenderId, userId).eq(Message::getReceiverId, peerId))
                                .or().nested(n -> n.eq(Message::getSenderId, peerId).eq(Message::getReceiverId, userId)))
                        .orderByDesc(Message::getCreatedAt));
        return PageResult.of(pageData.convert(this::toVo));
    }

    @Override
    @Transactional
    public MessageVO send(long userId, SendMessageRequest request) {
        userService.requireActiveUser(userId);
        userService.requireActiveUser(request.getPeerId());
        Message msg = new Message();
        msg.setSenderId(userId);
        msg.setReceiverId(request.getPeerId());
        msg.setContent(request.getContent());
        msg.setType(request.getType() != null ? request.getType() : "text");
        msg.setIsRead(0);
        messageMapper.insert(msg);
        upsertConversation(userId, request.getPeerId(), request.getContent(), false);
        upsertConversation(request.getPeerId(), userId, request.getContent(), true);
        return toVo(msg);
    }

    private void upsertConversation(long userId, long peerId, String lastMessage, boolean incrementUnread) {
        Conversation c = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId).eq(Conversation::getPeerId, peerId));
        if (c == null) {
            c = new Conversation();
            c.setUserId(userId);
            c.setPeerId(peerId);
            c.setUnread(0);
        }
        c.setLastMessage(lastMessage);
        c.setLastTime(LocalDateTime.now());
        if (incrementUnread) {
            c.setUnread((c.getUnread() != null ? c.getUnread() : 0) + 1);
        }
        if (c.getId() == null) {
            conversationMapper.insert(c);
        } else {
            conversationMapper.updateById(c);
        }
    }

    private MessageVO toVo(Message m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setSenderId(m.getSenderId());
        vo.setContent(m.getContent());
        vo.setType(m.getType());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }
}
