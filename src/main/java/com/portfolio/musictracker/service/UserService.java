package com.portfolio.musictracker.service;

import com.portfolio.musictracker.dto.PasswordChangeForm;
import com.portfolio.musictracker.dto.ProfileForm;
import com.portfolio.musictracker.dto.SignupForm;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザー登録・取得を担うサービス。パスワードは保存前に必ずハッシュ化する。
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 新規ユーザーを登録する。ユーザー名・メールの重複はエラーとする。
     *
     * @throws IllegalArgumentException 重複や不正入力があった場合
     */
    @Transactional
    public User register(SignupForm form) {
        String username = form.getUsername() == null ? "" : form.getUsername().trim();
        String email = form.getEmail() == null ? "" : form.getEmail().trim();
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("そのユーザー名はすでに使われています");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("そのメールアドレスはすでに登録されています");
        }
        User user = new User(username, passwordEncoder.encode(form.getPassword()), email, "USER");
        return userRepository.save(user);
    }

    /** ID 指定でユーザーを取得する。 */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: id=" + id));
    }

    /**
     * 基本情報（ユーザー名・メール）を更新する。
     * 他ユーザーとの重複はエラーとし、DBの一意制約違反もハンドリングする。
     *
     * @return 更新後のユーザー
     */
    @Transactional
    public User updateProfile(Long userId, ProfileForm form) {
        User user = findById(userId);
        String username = form.getUsername() == null ? "" : form.getUsername().trim();
        String email = form.getEmail() == null ? "" : form.getEmail().trim();

        // 自分以外で同名／同メールが存在する場合はエラー
        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("そのユーザー名はすでに使われています");
        }
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("そのメールアドレスはすでに登録されています");
        }

        user.setUsername(username);
        user.setEmail(email);
        try {
            // flush して一意制約違反をこの場で検知する
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("ユーザー名またはメールアドレスがすでに使われています");
        }
    }

    /**
     * パスワードを変更する。現在のパスワードが一致した場合のみ、
     * 新しいパスワードをハッシュ化して保存する。
     */
    @Transactional
    public void changePassword(Long userId, PasswordChangeForm form) {
        User user = findById(userId);
        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("現在のパスワードが正しくありません");
        }
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        userRepository.save(user);
    }
}
