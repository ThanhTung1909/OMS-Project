package com.oms.profile;

import com.oms.profile.dto.AccountCreatedEvent;
import com.oms.profile.entity.Department;
import com.oms.profile.entity.Staff;
import com.oms.profile.listener.AccountEventListener;
import com.oms.profile.repository.CustomerRepository;
import com.oms.profile.repository.DepartmentRepository;
import com.oms.profile.repository.ProcessedEventRepository;
import com.oms.profile.repository.StaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProfileListenerUnitTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private AccountEventListener accountEventListener;

    @Test
    public void testIdempotency_ShouldSkipIfProcessed() {
        // 1. Giả lập sự kiện đã được xử lý
        AccountCreatedEvent event = AccountCreatedEvent.builder().accountId("acc1").build();
        when(processedEventRepository.existsById("acc1")).thenReturn(true);

        // 2. Chạy listener
        accountEventListener.handleAccountCreated(event);

        // 3. Xác nhận không có repository nào lưu dữ liệu
        verify(customerRepository, never()).save(any());
        verify(staffRepository, never()).save(any());
        System.out.println("Test Idempotency thành công: Đã bỏ qua sự kiện trùng lặp.");
    }

    @Test
    public void testStaffCreation_ShouldAssignDefaultDepartment() {
        // 1. Chuẩn bị dữ liệu STAFF
        AccountCreatedEvent event = AccountCreatedEvent.builder()
                .accountId("acc2")
                .role("STAFF")
                .userName("staff1")
                .fullname("Staff Test")
                .build();
        
        when(processedEventRepository.existsById("acc2")).thenReturn(false);
        when(departmentRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(departmentRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // 2. Chạy listener
        accountEventListener.handleAccountCreated(event);

        // 3. Xác nhận Staff được lưu với Department
        verify(staffRepository).save(argThat(staff -> 
            staff.getDepartment() != null && staff.getDepartment().getName().equals("Chưa phân bổ")
        ));
        System.out.println("Test Staff Creation thành công: Đã gán phòng ban mặc định.");
    }
}
