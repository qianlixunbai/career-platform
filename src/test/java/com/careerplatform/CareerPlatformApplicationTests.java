package com.careerplatform;

import com.careerplatform.user.entity.AppUser;
import com.careerplatform.user.enums.UserStatus;
import com.careerplatform.user.exception.UsernameAlreadyExistsException;
import com.careerplatform.user.mapper.AppUserMapper;
import com.careerplatform.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CareerPlatformApplicationTests {

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextLoads() {
    }

    @Test
    void appUserMapperCanQueryDatabase() {
        Long count = appUserMapper.selectCount(null);
        System.out.println("app_user count = " + count);
    }

    @Test
    @Transactional
    void appUserCanInsertAndQuery() {
        String username = uniqueUsername("mapper_");
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("test_password_hash");
        user.setStatus(UserStatus.ACTIVE);

        int rows = appUserMapper.insert(user);
        LambdaQueryWrapper<AppUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AppUser::getUsername, username);
        AppUser queriedUser = appUserMapper.selectOne(queryWrapper);
        assertNotNull(queriedUser);
        assertEquals(user.getId(), queriedUser.getId());

        assertEquals(1, rows);
        assertNotNull(user.getId());
        AppUser savedUser = appUserMapper.selectById(user.getId());
        assertNotNull(savedUser);
        assertEquals(username, savedUser.getUsername());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertNotNull(savedUser.getCreatedAt());
        assertNotNull(savedUser.getUpdatedAt());
        assertEquals("test_password_hash", savedUser.getPasswordHash());
    }


    @Test
    @Transactional
    void usernameExistsShouldReturnFalseThenTrue() {
        String username = uniqueUsername("exists_");
        boolean existsBeforeInsert = userService.usernameExists(username);
        assertFalse(existsBeforeInsert);
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("test_password_hash");
        user.setStatus(UserStatus.ACTIVE);

        appUserMapper.insert(user);

        boolean existsAfterInsert = userService.usernameExists(username);
        assertTrue(existsAfterInsert);

    }


    @Test
    @Transactional
    void registerShouldCreateUser() {
        String username = uniqueUsername("register_");
        AppUser user = userService.register(username, "123456");
        assertNotNull(user.getId());
        assertEquals(username, user.getUsername());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNotEquals("123456", user.getPasswordHash());
        assertTrue(passwordEncoder.matches("123456", user.getPasswordHash()));

    }


    @Test
    @Transactional
    void registerShouldRejectDuplicateUsername() {
        String username = "service_" + UUID.randomUUID().toString().replace("-", "");
        userService.register(username, "123456");

        assertThrows(
                UsernameAlreadyExistsException.class,
                () -> userService.register(username, "123456")
        );
    }

    private String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
