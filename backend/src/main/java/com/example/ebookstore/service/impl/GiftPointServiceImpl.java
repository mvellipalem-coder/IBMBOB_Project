package com.example.ebookstore.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.domain.GiftPointTransactionType;
import com.example.ebookstore.dto.GiftPointBalanceResponse;
import com.example.ebookstore.dto.GiftPointTransactionPage;
import com.example.ebookstore.entity.GiftPointTransaction;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.GiftPointMapper;
import com.example.ebookstore.repository.GiftPointTransactionRepository;
import com.example.ebookstore.repository.UserRepository;
import com.example.ebookstore.service.GiftPointService;

/** Gift-point reads. Every query is scoped to the user id from the token. */
@Service
public class GiftPointServiceImpl implements GiftPointService {

    private final UserRepository userRepository;
    private final GiftPointTransactionRepository ledger;
    private final GiftPointMapper mapper;
    private final BusinessProperties business;

    public GiftPointServiceImpl(UserRepository userRepository, GiftPointTransactionRepository ledger,
                                GiftPointMapper mapper, BusinessProperties business) {
        this.userRepository = userRepository;
        this.ledger = ledger;
        this.mapper = mapper;
        this.business = business;
    }

    @Override
    @Transactional(readOnly = true)
    public GiftPointBalanceResponse getBalance(long userId) {
        int balance = userRepository.findPointsBalance(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return new GiftPointBalanceResponse(balance, business.pointValue(), business.maxRedeemPercentOfSubtotal(),
                business.earnAmountPerPoint());
    }

    @Override
    @Transactional(readOnly = true)
    public GiftPointTransactionPage listTransactions(long userId, GiftPointTransactionType type, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<GiftPointTransaction> result = type == null
                ? ledger.findByUserId(userId, pageable)
                : ledger.findByUserIdAndType(userId, type, pageable);
        return GiftPointTransactionPage.of(result.map(mapper::toResponse));
    }
}
