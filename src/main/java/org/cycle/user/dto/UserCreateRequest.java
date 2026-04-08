package org.cycle.user.dto;

import lombok.Data;

/**
 * Create user request DTO. Basic validation is performed in controller.
 */
@Data
public class UserCreateRequest {
    private String username;

    private String password;

    private String displayName;

    private String email;

    private String mobile;

    private Integer status = 1;
    private Integer isSuper = 0;
}


