package com.example.ebookstore.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.dto.AddressRequest;
import com.example.ebookstore.dto.AddressResponse;
import com.example.ebookstore.entity.Address;
import com.example.ebookstore.entity.User;
import com.example.ebookstore.entity.Role;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.AddressMapper;
import com.example.ebookstore.repository.AddressRepository;
import com.example.ebookstore.repository.UserRepository;

/**
 * Unit tests for {@link AddressServiceImpl}. Repositories are mocked — no database required.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressServiceImplTest {

    private static final long USER_ID = 42L;
    private static final long ADDR_ID = 100L;

    private static final BusinessProperties BUSINESS = new BusinessProperties(10, 10, new BigDecimal("1.00"), 50,
            new BigDecimal("10.00"), 48, 30, 24, 5, 2, List.of());

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private UserRepository userRepository;

    private AddressServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AddressServiceImpl(addressRepository, userRepository, new AddressMapper(), BUSINESS);
        // By default the user lock succeeds.
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
    }

    // ------------------------------------------------------------------ list

    @Test
    void listReturnsAddressesMappedFromRepository() {
        Address addr = address(ADDR_ID, USER_ID, "Home", true);
        when(addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(addr));

        List<AddressResponse> result = service.list(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(ADDR_ID);
        assertThat(result.get(0).recipientName()).isEqualTo("Home");
        assertThat(result.get(0).isDefault()).isTrue();
    }

    @Test
    void listReturnsEmptyListWhenUserHasNoAddresses() {
        when(addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of());

        assertThat(service.list(USER_ID)).isEmpty();
    }

    // ------------------------------------------------------------------ create

    @Test
    void firstAddressAlwaysBecomesTheDefault() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(addressRepository.saveAndFlush(any())).thenAnswer(call -> {
            Address a = call.getArgument(0);
            ReflectionTestUtils.setField(a, "id", ADDR_ID);
            return a;
        });

        AddressResponse result = service.create(USER_ID, addressRequest("Alice", false));

        assertThat(result.isDefault()).isTrue();
        verify(addressRepository).clearDefault(USER_ID);
    }

    @Test
    void secondAddressIsNotDefaultUnlessRequestedExplicitly() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(addressRepository.saveAndFlush(any())).thenAnswer(call -> {
            Address a = call.getArgument(0);
            ReflectionTestUtils.setField(a, "id", ADDR_ID + 1);
            return a;
        });

        AddressResponse result = service.create(USER_ID, addressRequest("Bob", false));

        assertThat(result.isDefault()).isFalse();
        verify(addressRepository, never()).clearDefault(anyLong());
    }

    @Test
    void createWithIsDefaultTrueBecomesTheDefaultEvenWhenOtherAddressesExist() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(2L);
        when(addressRepository.saveAndFlush(any())).thenAnswer(call -> {
            Address a = call.getArgument(0);
            ReflectionTestUtils.setField(a, "id", ADDR_ID);
            return a;
        });

        AddressResponse result = service.create(USER_ID, addressRequest("Charlie", true));

        assertThat(result.isDefault()).isTrue();
        verify(addressRepository).clearDefault(USER_ID);
    }

    @Test
    void createRejectsMoreThanTheMaximumNumberOfAddresses() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn((long) BUSINESS.maxAddressesPerUser());

        assertThatThrownBy(() -> service.create(USER_ID, addressRequest("Overflow", false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_LIMIT_REACHED));
        verify(addressRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ update

    @Test
    void updateReplacesFieldsAndReturnsUpdatedResponse() {
        Address existing = address(ADDR_ID, USER_ID, "Old Name", false);
        when(addressRepository.findByIdAndUserId(ADDR_ID, USER_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        AddressResponse result = service.update(USER_ID, ADDR_ID, addressRequest("New Name", false));

        assertThat(result.recipientName()).isEqualTo("New Name");
    }

    @Test
    void updateWithIsDefaultTruePromotesAddressToDefault() {
        Address existing = address(ADDR_ID, USER_ID, "Old", false);
        when(addressRepository.findByIdAndUserId(ADDR_ID, USER_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.update(USER_ID, ADDR_ID, addressRequest("Old", true));

        assertThat(existing.isDefaultAddress()).isTrue();
        verify(addressRepository).clearDefaultExcept(USER_ID, ADDR_ID);
    }

    @Test
    void updateWithIsDefaultFalseDoesNotRemoveDefaultFlagFromCurrentDefault() {
        Address existing = address(ADDR_ID, USER_ID, "Current Default", true);
        when(addressRepository.findByIdAndUserId(ADDR_ID, USER_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.update(USER_ID, ADDR_ID, addressRequest("Current Default", false));

        // clearDefaultExcept must NOT be called, and the address remains default
        verify(addressRepository, never()).clearDefaultExcept(anyLong(), anyLong());
        assertThat(existing.isDefaultAddress()).isTrue();
    }

    @Test
    void updateOfAnotherUsersAddressThrowsNotFound() {
        when(addressRepository.findByIdAndUserId(ADDR_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, ADDR_ID, addressRequest("Hacked", false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND));
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deleteRemovesAddressAndDoesNotChangeDefaultWhenNonDefaultIsDeleted() {
        Address nonDefault = address(ADDR_ID, USER_ID, "Non Default", false);
        when(addressRepository.findByIdAndUserId(ADDR_ID, USER_ID)).thenReturn(Optional.of(nonDefault));

        service.delete(USER_ID, ADDR_ID);

        verify(addressRepository).delete(nonDefault);
        verify(addressRepository, never()).findFirstByUserIdOrderByCreatedAtDescIdDesc(anyLong());
    }

    @Test
    void deletingTheDefaultAddressPromotesTheNewestRemainingAddress() {
        Address defaultAddr = address(ADDR_ID, USER_ID, "Default", true);
        Address nextNewest = address(ADDR_ID + 1, USER_ID, "Next", false);
        when(addressRepository.findByIdAndUserId(ADDR_ID, USER_ID)).thenReturn(Optional.of(defaultAddr));
        when(addressRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(Optional.of(nextNewest));

        service.delete(USER_ID, ADDR_ID);

        verify(addressRepository).delete(defaultAddr);
        assertThat(nextNewest.isDefaultAddress()).isTrue();
    }

    @Test
    void deleteOfAnotherUsersAddressThrowsNotFound() {
        when(addressRepository.findByIdAndUserId(ADDR_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, ADDR_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND));
        verify(addressRepository, never()).delete(any());
    }

    // ------------------------------------------------------------------ helpers

    private static User user(long id) {
        User u = new User("Test", "test@example.com", "hash", null, Role.CUSTOMER);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Address address(long id, long userId, String name, boolean isDefault) {
        Address a = BeanUtils.instantiateClass(Address.class);
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(a, "userId", userId);
        ReflectionTestUtils.setField(a, "recipientName", name);
        ReflectionTestUtils.setField(a, "phone", "+91 98765 43210");
        ReflectionTestUtils.setField(a, "line1", "12 MG Road");
        ReflectionTestUtils.setField(a, "line2", null);
        ReflectionTestUtils.setField(a, "city", "Pune");
        ReflectionTestUtils.setField(a, "state", "Maharashtra");
        ReflectionTestUtils.setField(a, "postalCode", "411001");
        ReflectionTestUtils.setField(a, "countryCode", "IN");
        ReflectionTestUtils.setField(a, "defaultAddress", isDefault);
        return a;
    }

    private static AddressRequest addressRequest(String name, boolean isDefault) {
        return new AddressRequest(name, "+91 98765 43210", "12 MG Road", null, "Pune", "Maharashtra", "411001", "IN",
                isDefault);
    }
}
