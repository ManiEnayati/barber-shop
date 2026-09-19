package com.example.barbershop.config;

import com.example.barbershop.entity.User;
import com.example.barbershop.repository.UserRepository;
import com.example.barbershop.service.IranianPhoneNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevelopmentAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final IranianPhoneNormalizer phoneNormalizer;
    private final String adminPhone;

    public DevelopmentAdminInitializer(
            UserRepository userRepository,
            IranianPhoneNormalizer phoneNormalizer,
            @Value("${app.dev.admin-phone:+989000000001}") String adminPhone
    ) {
        this.userRepository = userRepository;
        this.phoneNormalizer = phoneNormalizer;
        this.adminPhone = adminPhone;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String normalizedPhone = phoneNormalizer.normalize(adminPhone);
        User admin = userRepository.findByPhone(normalizedPhone)
                .orElseGet(() -> new User(normalizedPhone));
        admin.verifyPhone();
        admin.grantAdminRole();
        userRepository.save(admin);
    }
}
