package com.example.ebookstore.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.dto.AddressRequest;
import com.example.ebookstore.dto.AddressResponse;
import com.example.ebookstore.entity.Address;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.AddressMapper;
import com.example.ebookstore.repository.AddressRepository;
import com.example.ebookstore.repository.UserRepository;
import com.example.ebookstore.service.AddressService;

/**
 * Address rules.
 *
 * <ul>
 *   <li><b>Ownership:</b> every lookup includes the user id from the token ({@code findByIdAndUserId}).</li>
 *   <li><b>Concurrency:</b> every change first locks the user's row, so two simultaneous requests of one user
 *       cannot both pass the "at most 10 addresses" check or leave two default addresses (the database also
 *       forbids that with a unique partial index).</li>
 * </ul>
 */
@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final AddressMapper mapper;
    private final BusinessProperties business;

    public AddressServiceImpl(AddressRepository addressRepository, UserRepository userRepository,
                              AddressMapper mapper, BusinessProperties business) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.business = business;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> list(long userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(userId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse create(long userId, AddressRequest request) {
        lockUser(userId);
        long existing = addressRepository.countByUserId(userId);
        if (existing >= business.maxAddressesPerUser()) {
            throw new ApiException(ErrorCode.ADDRESS_LIMIT_REACHED,
                    "You can save at most " + business.maxAddressesPerUser() + " addresses.");
        }

        Address address = new Address(userId);
        apply(address, request);
        boolean makeDefault = existing == 0 || request.wantsDefault();
        if (makeDefault) {
            addressRepository.clearDefault(userId);
        }
        address.setDefaultAddress(makeDefault);
        return mapper.toResponse(addressRepository.saveAndFlush(address));
    }

    @Override
    @Transactional
    public AddressResponse update(long userId, long addressId, AddressRequest request) {
        lockUser(userId);
        Address address = findOwn(userId, addressId);
        apply(address, request);
        if (request.wantsDefault() && !address.isDefaultAddress()) {
            addressRepository.clearDefaultExcept(userId, addressId);
            address.setDefaultAddress(true);
        }
        // isDefault=false (or omitted) never removes the flag from the current default address.
        return mapper.toResponse(addressRepository.saveAndFlush(address));
    }

    @Override
    @Transactional
    public void delete(long userId, long addressId) {
        lockUser(userId);
        Address address = findOwn(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        // Run the delete now: the unique "one default" index must not see two defaults at once.
        addressRepository.flush();
        if (wasDefault) {
            addressRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
                    .ifPresent(newest -> newest.setDefaultAddress(true));
        }
    }

    // ------------------------------------------------------------------ helpers

    private void lockUser(long userId) {
        userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private Address findOwn(long userId, long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ADDRESS_NOT_FOUND));
    }

    private static void apply(Address address, AddressRequest request) {
        address.replaceWith(request.recipientName().trim(), request.phone(), request.line1().trim(),
                blankToNull(request.line2()), request.city().trim(), request.state().trim(),
                request.postalCode().trim(), request.countryCodeOrDefault());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
