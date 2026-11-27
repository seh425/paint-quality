package com.example.paintquality.user;

import com.example.paintquality.AppProperties;
import com.example.paintquality.auth.PasswordHasher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AppProperties properties;

    public BootstrapAdminInitializer(UserRepository userRepository, AppProperties properties) {
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByRole(Role.PRIMARY_ADMIN) > 0) {
            return;
        }
        String name = UserValues.name(properties.getBootstrapAdmin().getName());
        String area = UserValues.area(properties.getBootstrapAdmin().getArea());
        InspectorUser admin = userRepository.findByInspectorNameAndArea(name, area)
                .orElseGet(() -> new InspectorUser(name, area, Role.PRIMARY_ADMIN, true));
        admin.setRole(Role.PRIMARY_ADMIN);
        admin.setEnabled(true);
        if (admin.getPasswordHash() == null || admin.getPasswordHash().isBlank()) {
            admin.setPasswordHash(PasswordHasher.hash(properties.getBootstrapAdmin().getPassword()));
        }
        userRepository.save(admin);
    }
}
