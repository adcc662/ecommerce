package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.AddressRequest;
import com.example.ecommerce.models.dto.response.AddressResponse;
import com.example.ecommerce.models.entity.Address;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AddressResponse createAddress(String userEmail, AddressRequest request) {
        User user = findUser(userEmail);
        Address address = Address.builder().user(user).build();
        apply(address, request);
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            clearOtherDefaults(user.getId(), null);
        }
        return mapToResponse(addressRepository.save(address));
    }

    @Override
    public List<AddressResponse> getAddresses(String userEmail) {
        User user = findUser(userEmail);
        return addressRepository.findByUserId(user.getId()).stream()
                .map(AddressServiceImpl::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(String userEmail, Long id, AddressRequest request) {
        User user = findUser(userEmail);
        Address address = findOwnAddress(id, user);
        apply(address, request);
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            clearOtherDefaults(user.getId(), address.getId());
        }
        return mapToResponse(addressRepository.save(address));
    }

    /**
     * Addresses used by an order cannot be deleted (the database restricts it);
     * the flush surfaces that as a 409 instead of a failure at commit.
     */
    @Override
    @Transactional
    public void deleteAddress(String userEmail, Long id) {
        User user = findUser(userEmail);
        Address address = findOwnAddress(id, user);
        addressRepository.delete(address);
        addressRepository.flush();
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Address findOwnAddress(Long id, User user) {
        return addressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
    }

    private void clearOtherDefaults(Long userId, Long keepId) {
        addressRepository.findByUserIdAndIsDefaultTrue(userId).stream()
                .filter(other -> !other.getId().equals(keepId))
                .forEach(other -> {
                    other.setIsDefault(false);
                    addressRepository.save(other);
                });
    }

    private static void apply(Address address, AddressRequest request) {
        address.setAddressType(request.getAddressType());
        address.setStreetAddress(request.getStreetAddress());
        address.setApartment(request.getApartment());
        address.setCity(request.getCity());
        address.setStateProvince(request.getStateProvince());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());
        address.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
    }

    private static AddressResponse mapToResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .addressType(address.getAddressType())
                .streetAddress(address.getStreetAddress())
                .apartment(address.getApartment())
                .city(address.getCity())
                .stateProvince(address.getStateProvince())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
