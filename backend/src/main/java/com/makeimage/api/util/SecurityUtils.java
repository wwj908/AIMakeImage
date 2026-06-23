package com.makeimage.api.util;

import com.makeimage.api.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static CurrentUser currentUser() {
        CurrentUser currentUser = currentUserOrNull();
        if (currentUser == null) {
            throw new IllegalStateException("未登录");
        }
        return currentUser;
    }

    public static CurrentUser currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return null;
        }
        return currentUser;
    }
}
