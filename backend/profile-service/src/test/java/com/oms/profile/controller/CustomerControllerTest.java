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
    public void testUploadAvatar_Success() throws Exception {
        // Chuẩn bị dữ liệu ảo (Mock Data)
        String accountId = "CUS001";
        String fakeCloudUrl = "https://mock-cloud.com/fake-avatar.jpg";
        
        // Giả lập một file ảnh được gửi từ Frontend
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "test-image.jpg", 
                "image/jpeg", 
                "fake image content".getBytes()
        );

        // Giả lập hành vi: Khi UploadService được gọi, KHÔNG GỌI API THẬT, mà hãy trả về fakeCloudUrl
        Mockito.when(uploadService.uploadFile(any(), any())).thenReturn(fakeCloudUrl);
        
        // Giả lập CustomerService trả về DTO khi thành công
        CustomerProfileResponse mockResponse = CustomerProfileResponse.builder()
                .accountId(accountId)
                .avatarUrl(fakeCloudUrl)
                .build();
                
        Mockito.when(customerService.updateProfile(eq(accountId), any(UpdateProfileRequest.class)))
               .thenReturn(mockResponse);

        // Bắn request thử nghiệm
        mockMvc.perform(multipart("/api/v1/customers/me/avatar")
                .file(file)
                .header("X-Account-Id", accountId)
                .header("X-User-Role", "CUSTOMER"))
                
                // Kỳ vọng kết quả
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.avatarUrl").value(fakeCloudUrl));
    }
}
