package org.example.schoolshop.service;

import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.dto.req.SendMessageRequest;
import org.example.schoolshop.dto.vo.ConversationVO;
import org.example.schoolshop.dto.vo.MessageVO;

import java.util.List;
import java.util.Map;

public interface MessageService {

    Map<String, List<ConversationVO>> conversations(long userId);

    PageResult<MessageVO> chatMessages(long userId, long peerId, Integer page, Integer pageSize);

    MessageVO send(long userId, SendMessageRequest request);
}
