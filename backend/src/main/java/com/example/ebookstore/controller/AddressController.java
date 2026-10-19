package com.example.ebookstore.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.dto.AddressRequest;
import com.example.ebookstore.dto.AddressResponse;
import com.example.ebookstore.security.AuthenticatedUser;
import com.example.ebookstore.service.AddressService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

/**
 * Delivery-address endpoints (operations listAddresses, createAddress, updateAddress and deleteAddress in
 * openapi.yaml). The owner is always the token's user; there is no user id anywhere in the request.
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /** {@code GET /api/addresses} - default first, then newest first. */
    @GetMapping
    public List<AddressResponse> listAddresses(@AuthenticationPrincipal AuthenticatedUser user) {
        return addressService.list(user.id());
    }

    /** {@code POST /api/addresses} - 201 with a {@code Location} header. */
    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @Valid @RequestBody AddressRequest request) {
        AddressResponse created = addressService.create(user.id(), request);
        return ResponseEntity.created(URI.create("/api/addresses/" + created.id())).body(created);
    }

    /** {@code PUT /api/addresses/{id}} - full replacement. */
    @PutMapping("/{id}")
    public AddressResponse updateAddress(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable @Min(1) long id,
                                         @Valid @RequestBody AddressRequest request) {
        return addressService.update(user.id(), id, request);
    }

    /** {@code DELETE /api/addresses/{id}} - 204. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable @Min(1) long id) {
        addressService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
