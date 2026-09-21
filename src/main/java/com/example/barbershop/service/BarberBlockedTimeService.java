package com.example.barbershop.service;

import com.example.barbershop.dto.BarberBlockedTimeCreateRequest;
import com.example.barbershop.dto.BlockedTimeResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.exception.BlockedTimeNotFoundException;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import com.example.barbershop.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BarberBlockedTimeService {

    private final UserRepository userRepository;
    private final BarberRepository barberRepository;
    private final BlockedTimeRepository blockedTimeRepository;
    private final BlockedTimeService blockedTimeService;

    public BarberBlockedTimeService(UserRepository userRepository,
                                    BarberRepository barberRepository,
                                    BlockedTimeRepository blockedTimeRepository,
                                    BlockedTimeService blockedTimeService) {
        this.userRepository = userRepository;
        this.barberRepository = barberRepository;
        this.blockedTimeRepository = blockedTimeRepository;
        this.blockedTimeService = blockedTimeService;
    }

    @Transactional
    public BlockedTimeResponse create(Long userId, BarberBlockedTimeCreateRequest request) {
        Barber barber = requireCurrentBarber(userId);
        return blockedTimeService.createForBarber(barber, request.date(), request.startTime(),
                request.endTime(), request.reason().trim());
    }

    @Transactional(readOnly = true)
    public List<BlockedTimeResponse> list(Long userId, LocalDate date) {
        Barber barber = requireCurrentBarber(userId);
        return blockedTimeService.findByBarberAndDate(barber.getId(), date);
    }

    @Transactional
    public void delete(Long userId, Long blockedTimeId) {
        Barber barber = requireCurrentBarber(userId);
        BlockedTime blockedTime = blockedTimeRepository.findById(blockedTimeId)
                .orElseThrow(() -> new BlockedTimeNotFoundException(blockedTimeId));
        if (!blockedTime.getBarber().getId().equals(barber.getId())) {
            throw new AccessDeniedException("Blocked time belongs to another barber");
        }
        blockedTimeService.delete(blockedTimeId);
    }

    private Barber requireCurrentBarber(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isPhoneVerified)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is unavailable"));
        if (!user.getRoles().contains(UserRole.BARBER)) {
            throw new AccessDeniedException("Barber role is required");
        }
        return barberRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException("Linked barber profile is required"));
    }
}
