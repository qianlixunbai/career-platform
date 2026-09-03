package com.careerplatform.user.service;

import com.careerplatform.auth.JwtTokenService;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.user.entity.AppUser;
import com.careerplatform.user.enums.UserStatus;
import com.careerplatform.user.exception.InvalidCredentialsException;
import com.careerplatform.user.exception.UsernameAlreadyExistsException;
import com.careerplatform.user.mapper.AppUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class UserService {

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public UserService(
            AppUserMapper appUserMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }
    public boolean usernameExists(String username) {
        LambdaQueryWrapper<AppUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AppUser::getUsername, username);

        Long count = appUserMapper.selectCount(queryWrapper);

        return count > 0;
    }
    @Transactional
    public AppUser register(String username, String password) {
        validateBcryptPasswordLength(password);
        if (usernameExists(username)) {
            throw new UsernameAlreadyExistsException("用户名已存在");
        }
        String passwordHash = passwordEncoder.encode(password);
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setStatus(UserStatus.ACTIVE);
        try {
            appUserMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new UsernameAlreadyExistsException("用户名已存在");
        }

        return user;
    }

    public LoginResult login(String username, String rawPassword) {
        validateBcryptPasswordLength(rawPassword);
        LambdaQueryWrapper<AppUser> query = new LambdaQueryWrapper<>();
        query.eq(AppUser::getUsername, username);
        AppUser user = appUserMapper.selectOne(query);
        if (user == null
                || user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("用户名或密码错误");
        }
        String token = jwtTokenService.createToken(user.getId(), user.getUsername());
        return new LoginResult(token, user.getId(), user.getUsername(), user.getStatus());
    }

    public boolean isActiveUser(Long userId) {
        return appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .eq(AppUser::getStatus, UserStatus.ACTIVE)) == 1;
    }

    private void validateBcryptPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException("密码 UTF-8 编码长度不能超过72字节");
        }
    }

}
