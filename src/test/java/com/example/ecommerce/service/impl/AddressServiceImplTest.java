package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.AddressRequest;
import com.example.ecommerce.models.entity.Address;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.models.enums.AddressType;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AddressServiceImpl addressService;

    private final User user = User.builder().id(10L).email(EMAIL).build();

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AddressRequest request(boolean isDefault) {
        return AddressRequest.builder().addressType(AddressType.BOTH).streetAddress("1 Main St").city("Springfield")
                .stateProvince("IL").postalCode("62701").country("US").isDefault(isDefault).build();
    }

    @Test
    void createAssignsAddressToAuthenticatedUser() {
        var response = addressService.createAddress(EMAIL, request(false));

        ArgumentCaptor<Address> saved = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(response.getCity()).isEqualTo("Springfield");
        assertThat(response.getIsDefault()).isFalse();
    }

    @Test
    void newDefaultAddressClearsPreviousDefault() {
        Address previous = Address.builder().id(5L).user(user).isDefault(true).build();
        when(addressRepository.findByUserIdAndIsDefaultTrue(10L)).thenReturn(List.of(previous));

        addressService.createAddress(EMAIL, request(true));

        assertThat(previous.getIsDefault()).isFalse();
    }

    @Test
    void updateOfAnotherUsersAddressReturnsNotFound() {
        when(addressRepository.findByIdAndUserId(7L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(EMAIL, 7L, request(false)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Address not found");
        verify(addressRepository, never()).save(any());
    }

    @Test
    void deleteFlushesSoConstraintErrorsSurfaceInsideTheRequest() {
        Address address = Address.builder().id(7L).user(user).build();
        when(addressRepository.findByIdAndUserId(7L, 10L)).thenReturn(Optional.of(address));

        addressService.deleteAddress(EMAIL, 7L);

        InOrder order = inOrder(addressRepository);
        order.verify(addressRepository).delete(address);
        order.verify(addressRepository).flush();
    }

    @Test
    void deleteOfAnotherUsersAddressReturnsNotFound() {
        when(addressRepository.findByIdAndUserId(7L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(EMAIL, 7L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(addressRepository, never()).delete(any());
    }
}
