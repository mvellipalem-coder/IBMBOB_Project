package com.example.ebookstore.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ebookstore.dto.UserProfile;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.UserMapper;
import com.example.ebookstore.repository.UserRepository;
import com.example.ebookstore.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile getProfile(long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toProfile)
                // The account was removed after the token was issued.
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
