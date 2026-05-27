package com.oms.profile.controller;

import com.oms.common.service.UploadService;
import com.oms.profile.dto.CustomerProfileResponse;
import com.oms.profile.dto.UpdateProfileRequest;
import com.oms.profile.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false) // Tạm thời bỏ qua filter bảo mật để test controller trực tiếp
public class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    @MockBean
    private UploadService uploadService;

    @Test
    public void testUpdateProfile_WithAvatar_Success() throws Exception {
        String accountId = "CUS001";
        String fakeCloudUrl = "https://mock-cloud.com/fake-avatar.jpg";
        
        // Giả lập file ảnh từ frontend
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "test-image.jpg", 
                "image/jpeg", 
                "fake image content".getBytes()
        );

        Mockito.when(uploadService.uploadFile(any(), any())).thenReturn(fakeCloudUrl);
        
        CustomerProfileResponse mockResponse = CustomerProfileResponse.builder()
                .accountId(accountId)
                .fullname("Nguyen Van A")
                .phone("0987654321")
                .avatarUrl(fakeCloudUrl)
                .gender("MALE")
                .dateOfBirth("1995-05-20")
                .build();
                
        Mockito.when(customerService.updateProfile(eq(accountId), any(UpdateProfileRequest.class)))
               .thenReturn(mockResponse);

        // Bắn request PUT dạng multipart
        mockMvc.perform(multipart("/api/v1/customers/me")
                .file(file)
                .param("fullname", "Nguyen Van A")
                .param("phone", "0987654321")
                .param("gender", "MALE")
                .param("dateOfBirth", "1995-05-20")
                .header("X-Account-Id", accountId)
                .header("X-User-Role", "CUSTOMER")
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.fullname").value("Nguyen Van A"))
                .andExpect(jsonPath("$.result.avatarUrl").value(fakeCloudUrl))
                .andExpect(jsonPath("$.result.gender").value("MALE"));
        
        Mockito.verify(uploadService, Mockito.times(1)).uploadFile(eq(file), eq("avatars"));
    }

    @Test
    public void testUpdateProfile_WithoutAvatar_Success() throws Exception {
        String accountId = "CUS001";
        
        CustomerProfileResponse mockResponse = CustomerProfileResponse.builder()
                .accountId(accountId)
                .fullname("Nguyen Van B")
                .phone("0987654321")
                .avatarUrl("https://example.com/old-avatar.jpg")
                .gender("FEMALE")
                .dateOfBirth("1998-08-08")
                .build();
                
        Mockito.when(customerService.updateProfile(eq(accountId), any(UpdateProfileRequest.class)))
               .thenReturn(mockResponse);

        // Bắn request PUT dạng multipart nhưng KHÔNG truyền file
        mockMvc.perform(multipart("/api/v1/customers/me")
                .param("fullname", "Nguyen Van B")
                .param("phone", "0987654321")
                .param("gender", "FEMALE")
                .param("dateOfBirth", "1998-08-08")
                .header("X-Account-Id", accountId)
                .header("X-User-Role", "CUSTOMER")
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.fullname").value("Nguyen Van B"))
                .andExpect(jsonPath("$.result.gender").value("FEMALE"))
                .andExpect(jsonPath("$.result.dateOfBirth").value("1998-08-08"));
        
        Mockito.verify(uploadService, Mockito.never()).uploadFile(any(), any());
    }

    @Test
    public void testUpdateProfile_UploadAvatar_Failure() throws Exception {
        String accountId = "CUS001";
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "test-image.jpg", 
                "image/jpeg", 
                "fake image content".getBytes()
        );

        // Giả lập ngoại lệ IOException xảy ra khi upload file
        Mockito.when(uploadService.uploadFile(any(), any()))
               .thenThrow(new java.io.IOException("Cloud connection failed"));

        mockMvc.perform(multipart("/api/v1/customers/me")
                .file(file)
                .param("fullname", "Nguyen Van A")
                .header("X-Account-Id", accountId)
                .header("X-User-Role", "CUSTOMER")
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Lỗi hệ thống nghiêm trọng"));
    }
}
