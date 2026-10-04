package com.example.barbershop.service;

import com.example.barbershop.dto.BarberServiceManagementRequest;
import com.example.barbershop.dto.BarberServiceResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.exception.BarberServiceOfferingNotFoundException;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BarberServiceManagementService {

    private final UserRepository userRepository;
    private final BarberRepository barberRepository;
    private final BarberServiceOfferingRepository offeringRepository;
    private final BarberServiceOfferingService offeringService;

    public BarberServiceManagementService(
            UserRepository userRepository,
            BarberRepository barberRepository,
            BarberServiceOfferingRepository offeringRepository,
            BarberServiceOfferingService offeringService
    ) {
        this.userRepository = userRepository;
        this.barberRepository = barberRepository;
        this.offeringRepository = offeringRepository;
        this.offeringService = offeringService;
    }

    @Transactional
    public BarberServiceResponse create(
            Long userId,
            BarberServiceManagementRequest request
    ) {
        return offeringService.createForBarber(requireCurrentBarber(userId), request);
    }

    @Transactional
    public BarberServiceResponse update(
            Long userId,
            Long serviceId,
            BarberServiceManagementRequest request
    ) {
        Barber barber = requireCurrentBarber(userId);
        return offeringService.updateForBarber(
                requireOwnedOffering(barber, serviceId), request);
    }

    @Transactional
    public void delete(Long userId, Long serviceId) {
        Barber barber = requireCurrentBarber(userId);
        offeringService.deleteForBarber(requireOwnedOffering(barber, serviceId));
    }

    private BarberServiceOffering requireOwnedOffering(
            Barber barber,
            Long serviceId
    ) {
        BarberServiceOffering offering = offeringRepository.findById(serviceId)
                .orElseThrow(() -> new BarberServiceOfferingNotFoundException(serviceId));
        if (!offering.getBarber().getId().equals(barber.getId())) {
            throw new AccessDeniedException("Service belongs to another barber");
        }
        return offering;
    }

    private Barber requireCurrentBarber(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isPhoneVerified)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user is unavailable"));
        if (!user.getRoles().contains(UserRole.BARBER)) {
            throw new AccessDeniedException("Barber role is required");
        }
        return barberRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Linked barber profile is required"));
    }
}
