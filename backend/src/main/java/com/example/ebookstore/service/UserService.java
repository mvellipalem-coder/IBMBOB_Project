package com.example.ebookstore.service;

import com.example.ebookstore.dto.UserProfile;

/** Reading the profile of a user. */
public interface UserService {

    /**
     * Returns the profile of the given user.
     *
     * @param userId the id taken from the authenticated principal (never from the request)
     */
    UserProfile getProfile(long userId);
}
