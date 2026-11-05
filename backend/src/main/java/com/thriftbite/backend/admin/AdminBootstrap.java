package com.thriftbite.backend.admin;

import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Creates the first admin account from APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD
 * when the app starts. v1 hard-coded an admin username in the source; this
 * keeps credentials out of the repo entirely.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                          @Value("${app.admin.username:}") String username,
                          @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isEmpty() || password.isEmpty()) {
            if (!users.existsByRole(Role.ADMIN)) {
                log.info("No admin account yet. Set APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD to create one.");
            }
            return;
        }
        if (password.length() < 12) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD must be at least 12 characters long");
        }
        if (users.existsByUsername(username)) {
            return;   // already created on an earlier start
        }
        users.save(new User(username, passwordEncoder.encode(password), Role.ADMIN));
        log.info("Created admin account '{}'", username);
    }
}
