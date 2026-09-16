package com.example.barbershop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    protected Customer() {
    }

    public Customer(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public Customer(User user, String name) {
        if (user == null || !user.isPhoneVerified()
                || !user.getRoles().contains(UserRole.CUSTOMER)) {
            throw new IllegalArgumentException("Verified customer user is required");
        }
        this.user = user;
        this.name = name;
        this.phone = user.getPhone();
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

    public User getUser() {
        return user;
    }

    public void updateName(String name) {
        this.name = name;
    }
}
