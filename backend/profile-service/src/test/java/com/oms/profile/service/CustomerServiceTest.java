package com.oms.profile.service;

import com.oms.profile.dto.CustomerProfileResponse;
import com.oms.profile.dto.UpdateProfileRequest;
import com.oms.profile.entity.Customer;
import com.oms.profile.entity.Gender;
import com.oms.profile.repository.CustomerAddressRepository;
import com.oms.profile.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerAddressRepository addressRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer existingCustomer;
    private final String accountId = "CUS001";

    @BeforeEach
    public void setUp() {
        existingCustomer = new Customer();
        existingCustomer.setId("1");
        existingCustomer.setAccountId(accountId);
        existingCustomer.setFullname("Nguyen Cuu Long");
        existingCustomer.setPhone("0123456789");
        existingCustomer.setAvatarUrl("https://example.com/old-avatar.jpg");
        existingCustomer.setGender(Gender.MALE);
        existingCustomer.setDateOfBirth(LocalDate.of(1990, 1, 1));
        existingCustomer.setCustomerAddresses(new ArrayList<>());
    }

    @Test
    public void testUpdateProfile_AllFields_Success() {
        // Prepare request
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullname("Tran Van B")
                .phone("0987654321")
                .avatarUrl("https://example.com/new-avatar.jpg")
                .gender("FEMALE")
                .dateOfBirth(LocalDate.of(1998, 8, 8))
                .build();

        // Stub findByAccountId
        Mockito.when(customerRepository.findByAccountId(eq(accountId)))
                .thenReturn(Optional.of(existingCustomer));

        // Stub save
        Mockito.when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CustomerProfileResponse response = customerService.updateProfile(accountId, request);

        // Assert
        assertNotNull(response);
        assertEquals("Tran Van B", response.getFullname());
        assertEquals("0987654321", response.getPhone());
        assertEquals("https://example.com/new-avatar.jpg", response.getAvatarUrl());
        assertEquals("FEMALE", response.getGender());
        assertEquals("1998-08-08", response.getDateOfBirth());

        // Verify save was called
        Mockito.verify(customerRepository, Mockito.times(1)).save(existingCustomer);
    }

    @Test
    public void testUpdateProfile_PartialFields() {
        // Prepare request (only update fullname & phone, keep other fields)
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullname("Tran Van C")
                .phone("0900000000")
                .build();

        // Stub findByAccountId
        Mockito.when(customerRepository.findByAccountId(eq(accountId)))
                .thenReturn(Optional.of(existingCustomer));

        // Stub save
        Mockito.when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CustomerProfileResponse response = customerService.updateProfile(accountId, request);

        // Assert
        assertNotNull(response);
        assertEquals("Tran Van C", response.getFullname());
        assertEquals("0900000000", response.getPhone());
        // Remaining fields should be kept as before
        assertEquals("https://example.com/old-avatar.jpg", response.getAvatarUrl());
        assertEquals("MALE", response.getGender());
        assertEquals("1990-01-01", response.getDateOfBirth());
    }

    @Test
    public void testUpdateProfile_InvalidGender_ShouldIgnore() {
        // Prepare request with invalid gender
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .gender("INVALID_GENDER_NAME")
                .build();

        // Stub findByAccountId
        Mockito.when(customerRepository.findByAccountId(eq(accountId)))
                .thenReturn(Optional.of(existingCustomer));

        // Stub save
        Mockito.when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CustomerProfileResponse response = customerService.updateProfile(accountId, request);

        // Assert
        assertNotNull(response);
        // Gender should remain MALE, not throw Exception
        assertEquals("MALE", response.getGender());
        assertEquals("Nguyen Cuu Long", response.getFullname());
    }

    @Test
    public void testUpdateProfile_CustomerNotFound_ShouldThrowException() {
        // Prepare request
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullname("Nguyen Van X")
                .build();

        // Stub findByAccountId to return empty
        Mockito.when(customerRepository.findByAccountId(eq("NON_EXIST")))
                .thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            customerService.updateProfile("NON_EXIST", request);
        });

        assertEquals("Customer not found", exception.getMessage());
        Mockito.verify(customerRepository, Mockito.never()).save(any(Customer.class));
    }
}
