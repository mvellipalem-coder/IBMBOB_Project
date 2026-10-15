package com.example.ebookstore.mapper;

import org.springframework.stereotype.Component;

import com.example.ebookstore.dto.UserProfile;
import com.example.ebookstore.entity.User;

/** Converts a {@link User} entity into the {@link UserProfile} DTO. Controllers never see the entity. */
@Component
public class UserMapper {

    public UserProfile toProfile(User user) {
        return new UserProfile(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getRole(), user.getCreatedAt());
    }
}
