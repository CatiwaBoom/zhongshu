package org.cycle.user.dto;

import lombok.Data;

/**
 * Update user request DTO. Password is optional; null/empty means no change.
 */
@Data
public class UserUpdateRequest {
    private String username;

    private String password; // optional: if empty/null -> do not update

    private String displayName;

    private String email;

    private String mobile;

    private Integer status;
    private Integer isSuper;
}


