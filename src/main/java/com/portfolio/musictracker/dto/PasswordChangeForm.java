package com.portfolio.musictracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * パスワード変更フォーム。
 */
public class PasswordChangeForm {

    @NotBlank(message = "現在のパスワードを入力してください")
    private String currentPassword;

    @NotBlank(message = "新しいパスワードを入力してください")
    @Size(min = 6, max = 100, message = "新しいパスワードは6文字以上で入力してください")
    private String newPassword;

    @NotBlank(message = "確認用パスワードを入力してください")
    private String confirmPassword;

    /** 新パスワードと確認用が一致しているか。 */
    public boolean isConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
