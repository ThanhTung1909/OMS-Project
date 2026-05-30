package com.oms.profile.service;

import com.oms.profile.dto.AddressRequest;
import com.oms.profile.dto.AddressResponse;
import com.oms.profile.dto.CustomerProfileResponse;
import com.oms.profile.dto.UpdateProfileRequest;
import com.oms.profile.entity.Customer;
import com.oms.profile.entity.CustomerAddress;
import com.oms.profile.entity.Gender;
import com.oms.profile.repository.CustomerAddressRepository;
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
    private  final CustomerAddressRepository addressRepository;

    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfileByAccountId(String accountId) {
        Customer customer = customerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new RuntimeException("Customer not found for accountId: " + accountId));
        return mapToResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfileById(String id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
        return mapToResponse(customer);
    }

    private CustomerProfileResponse mapToResponse(Customer customer) {
        List<AddressResponse> addressDtos = customer.getCustomerAddresses().stream()
                .filter(addr -> addr.isActive())
                .map(addr -> AddressResponse.builder()
                        .street(addr.getStreet())
                        .ward(addr.getWard())
                        .city(addr.getCity())
                        .isDefault(addr.isDefault())
                        .build())
                .collect(Collectors.toList());

        return CustomerProfileResponse.builder()
                .id(customer.getId())
                .fullname(customer.getFullname())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .avatarUrl(customer.getAvatarUrl())
                .gender(customer.getGender() != null ? customer.getGender().name() : null)
                .dateOfBirth(customer.getDateOfBirth() != null ? customer.getDateOfBirth().toString() : null)
                .addresses(addressDtos)
                .accountId(customer.getAccountId())
                .build();
    }

    @Transactional
    public void addAddress(String accountId, AddressRequest request) {
        Customer customer = customerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        // Nếu đặt là mặc định, reset các địa chỉ cũ
        if (request.isDefault()) {
            addressRepository.findByCustomerIdAndIsDefaultTrue(customer.getId())
                    .forEach(addr -> {
                        addr.setDefault(false);
                        addressRepository.save(addr);
                    });
        }

        CustomerAddress address = new CustomerAddress();
        address.setStreet(request.getStreet());
        address.setWard(request.getWard());
        address.setCity(request.getCity());
        address.setDefault(request.isDefault());
        address.setCustomer(customer);
        address.setActive(true);

        addressRepository.save(address);
    }

    @Transactional
    public void setDefaultAddress(String accountId, String addressId) {
        Customer customer = customerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        // Reset mặc định cũ
        addressRepository.findByCustomerIdAndIsDefaultTrue(customer.getId())
                .forEach(addr -> {
                    addr.setDefault(false);
                    addressRepository.save(addr);
                });

        // Set mặc định mới
        CustomerAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        
        if (!address.getCustomer().getId().equals(customer.getId())) {
            throw new RuntimeException("Address does not belong to this customer");
        }

        address.setDefault(true);
        addressRepository.save(address);
    }

    @Transactional(readOnly = true)
    public List<CustomerProfileResponse> getAllCustomers() {
        return customerRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    @Transactional
    public CustomerProfileResponse updateProfile(String accountId, UpdateProfileRequest request) {
        Customer customer = customerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        if (request.getFullname() != null) customer.setFullname(request.getFullname());
        if (request.getPhone() != null) customer.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null) customer.setAvatarUrl(request.getAvatarUrl());
        if (request.getDateOfBirth() != null) customer.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) {
            try {
                customer.setGender(Gender.valueOf(request.getGender().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Ignore invalid gender
            }
        }

        return mapToResponse(customerRepository.save(customer));
    }
}
