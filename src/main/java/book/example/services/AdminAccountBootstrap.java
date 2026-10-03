package book.example.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountBootstrap implements ApplicationRunner {
    private final UserService userService;
    private final String email;
    private final String name;
    private final String password;

    public AdminAccountBootstrap(
            UserService userService,
            @Value("${app.admin.bootstrap.email:}") String email,
            @Value("${app.admin.bootstrap.name:Administrator}") String name,
            @Value("${app.admin.bootstrap.password:}") String password) {
        this.userService = userService;
        this.email = email;
        this.name = name;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean emailConfigured = email != null && !email.isBlank();
        boolean passwordConfigured = password != null && !password.isBlank();
        if (!emailConfigured && !passwordConfigured) return;
        if (!emailConfigured || !passwordConfigured) {
            throw new IllegalStateException(
                    "Configure both ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD, or leave both unset.");
        }
        userService.bootstrapAdmin(email, name, password);
    }
}
