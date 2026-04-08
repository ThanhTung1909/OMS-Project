package com.oms.profile.service;

import com.oms.profile.dto.AddressResponse;
import com.oms.profile.dto.CustomerProfileResponse;
import com.oms.profile.entity.Customer;
import com.oms.profile.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private  final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfileByAccountId(String accountId) {

        // 1. Tìm Customer từ DB
        Customer customer = customerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new RuntimeException("Customer not found for accountId: " + accountId));

        // 2. Map danh sách Address Entity sang AddressResponse DTO
        List<AddressResponse> addressDtos = customer.getCustomerAddresses().stream()
                .filter(addr -> addr.isActive())
                .map(addr -> AddressResponse.builder()
                        .street(addr.getStreet())
                        .ward(addr.getWard())
                        .district(addr.getDistrict())
                        .city(addr.getCity())
                        .isDefault(addr.isDefault())
                        .build())
                .collect(Collectors.toList());

        // 3. Map Customer Entity sang CustomerProfileResponse DTO
        return CustomerProfileResponse.builder()
                .fullname(customer.getFullname())
                .phone(customer.getPhone())
                .avatarUrl(customer.getAvatarUrl())
                .gender(customer.getGender() != null ? customer.getGender().name() : null)
                .dateOfBirth(customer.getDateOfBirth() != null ? customer.getDateOfBirth().toString() : null)
                .addresses(addressDtos)
                .build();
    }
}
