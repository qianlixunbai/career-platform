package com.careerplatform.user.service;

import com.careerplatform.user.entity.AppUser;
import com.careerplatform.user.exception.UsernameAlreadyExistsException;
import com.careerplatform.user.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerShouldConvertDuplicateKeyException() {
        when(appUserMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        doThrow(new DuplicateKeyException("duplicate username"))
                .when(appUserMapper).insert(any(AppUser.class));

        UsernameAlreadyExistsException exception = assertThrows(
                UsernameAlreadyExistsException.class,
                () -> userService.register("race_user", "123456")
        );

        assertEquals("用户名已存在", exception.getMessage());
    }
}
