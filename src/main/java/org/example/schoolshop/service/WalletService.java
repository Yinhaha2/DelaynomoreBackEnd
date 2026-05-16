package org.example.schoolshop.service;

import org.example.schoolshop.dto.req.WithdrawRequest;
import org.example.schoolshop.dto.vo.WalletVO;

import java.util.Map;

public interface WalletService {

    WalletVO getWallet(long userId);

    Map<String, Object> withdraw(long userId, WithdrawRequest request);
}
