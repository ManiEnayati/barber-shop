package com.example.barbershop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;

@Entity
@Table(name = "barbers")
public class Barber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private LocalTime workStartTime;

    @Column(nullable = false)
    private LocalTime workEndTime;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    protected Barber() {
    }

    public Barber(String name, String phone, LocalTime workStartTime, LocalTime workEndTime) {
        this.name = name;
        this.phone = phone;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
    }

    public Barber(User user, String name, LocalTime workStartTime, LocalTime workEndTime) {
        if (user == null || !user.isPhoneVerified()
                || !user.getRoles().contains(UserRole.BARBER)) {
            throw new IllegalArgumentException("Verified barber user is required");
        }
        this.user = user;
        this.name = name;
        this.phone = user.getPhone();
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public LocalTime getWorkStartTime() {
        return workStartTime;
    }

    public LocalTime getWorkEndTime() {
        return workEndTime;
    }

    public User getUser() {
        return user;
    }
}
