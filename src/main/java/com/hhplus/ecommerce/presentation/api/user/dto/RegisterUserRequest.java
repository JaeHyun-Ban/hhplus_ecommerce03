package com.hhplus.ecommerce.presentation.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 사용자 등록 요청 DTO
 *
 * Use Case: UC-002
 * - 사용자 등록 시 클라이언트가 전송하는 데이터
 */
@Schema(description = "사용자 등록 요청")
@Getter
@NoArgsConstructor
public class RegisterUserRequest {

    @Schema(description = "이메일 주소", example = "user@example.com", required = true)
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String email;

    @Schema(description = "사용자 이름", example = "홍길동", required = true)
    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하여야 합니다")
    private String name;

    @Schema(description = "비밀번호", example = "password123", required = true)
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 6, max = 100, message = "비밀번호는 6자 이상 100자 이하여야 합니다")
    private String password;
}
