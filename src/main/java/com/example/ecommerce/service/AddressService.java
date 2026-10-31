package com.example.ecommerce.service;

import com.example.ecommerce.models.dto.request.AddressRequest;
import com.example.ecommerce.models.dto.response.AddressResponse;

import java.util.List;

public interface AddressService {
    AddressResponse createAddress(String userEmail, AddressRequest request);

    List<AddressResponse> getAddresses(String userEmail);

    AddressResponse updateAddress(String userEmail, Long id, AddressRequest request);

    void deleteAddress(String userEmail, Long id);
}
