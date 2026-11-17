package com.example.paintquality.user;

import com.example.paintquality.AppProperties;
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
        String name = UserValues.name(properties.getBootstrapAdmin().getName());
        String area = UserValues.area(properties.getBootstrapAdmin().getArea());
        InspectorUser admin = userRepository.findFirstByInspectorNameOrderByIdAsc(name)
                .orElseGet(() -> new InspectorUser(name, area, Role.PRIMARY_ADMIN, true));

        for (InspectorUser current : userRepository.findAllByRole(Role.PRIMARY_ADMIN)) {
            if (admin.getId() == null || !admin.getId().equals(current.getId())) {
                current.setRole(Role.USER);
                userRepository.save(current);
            }
        }
        admin.setRole(Role.PRIMARY_ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);
    }
}
