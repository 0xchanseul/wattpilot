package com.wattpilot.user.service;

import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.user.dto.UpdateUserRequest;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private static User registeredUser() {
        return User.register("iris@example.com", "hashed", "Iris", PriceArea.NO1);
    }

    @Test
    void updateProfileAppliesOnlyTheProvidedFieldsAndLeavesTheRestUnchanged() {
        User user = registeredUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateProfile(USER_ID, new UpdateUserRequest("  Iris Renamed  ", null));

        assertThat(updated.getName()).isEqualTo("Iris Renamed");
        assertThat(updated.getDefaultPriceArea()).isEqualTo(PriceArea.NO1);
    }

    @Test
    void updateProfileCanChangeOnlyTheDefaultPriceArea() {
        User user = registeredUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateProfile(USER_ID, new UpdateUserRequest(null, PriceArea.NO3));

        assertThat(updated.getName()).isEqualTo("Iris");
        assertThat(updated.getDefaultPriceArea()).isEqualTo(PriceArea.NO3);
    }

    @Test
    void updateProfileOnAnUnknownUserThrowsUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(USER_ID, new UpdateUserRequest("New name", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
