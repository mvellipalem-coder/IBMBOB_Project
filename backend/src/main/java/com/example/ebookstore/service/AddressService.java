package com.example.ebookstore.service;

import java.util.List;

import com.example.ebookstore.dto.AddressRequest;
import com.example.ebookstore.dto.AddressResponse;

/**
 * Delivery-address use cases. Every method takes the authenticated user's id (from the token) and only ever touches
 * that user's addresses; another user's address looks exactly like one that does not exist.
 */
public interface AddressService {

    /** The default address first, then newest first. */
    List<AddressResponse> list(long userId);

    /**
     * Saves a new address. The first address becomes the default automatically; {@code isDefault=true} moves the
     * default flag to the new address.
     *
     * @throws com.example.ebookstore.exception.ApiException {@code ADDRESS_LIMIT_REACHED}
     */
    AddressResponse create(long userId, AddressRequest request);

    /**
     * Replaces all fields. {@code isDefault=true} moves the default flag here; {@code false} never removes it.
     *
     * @throws com.example.ebookstore.exception.ApiException {@code ADDRESS_NOT_FOUND}
     */
    AddressResponse update(long userId, long addressId, AddressRequest request);

    /**
     * Deletes an address. If it was the default, the most recently created remaining address becomes the default.
     *
     * @throws com.example.ebookstore.exception.ApiException {@code ADDRESS_NOT_FOUND}
     */
    void delete(long userId, long addressId);
}
