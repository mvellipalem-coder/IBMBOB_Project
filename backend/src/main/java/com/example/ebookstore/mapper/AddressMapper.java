package com.example.ebookstore.mapper;

import org.springframework.stereotype.Component;

import com.example.ebookstore.dto.AddressResponse;
import com.example.ebookstore.entity.Address;

/** Converts {@link Address} entities to API responses. */
@Component
public class AddressMapper {

    public AddressResponse toResponse(Address address) {
        return new AddressResponse(address.getId(), address.getRecipientName(), address.getPhone(),
                address.getLine1(), address.getLine2(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getCountryCode(), address.isDefaultAddress());
    }
}
