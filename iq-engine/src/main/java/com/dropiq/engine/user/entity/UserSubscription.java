package com.dropiq.engine.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Embeddable
public class UserSubscription {
    @Column(name = "subscription_type")
    private String subscriptionType = "FREE"; // FREE, BASIC, PRO, ENTERPRISE

    @Column(name = "subscription_start")
    private LocalDateTime subscriptionStart = LocalDateTime.now();

    @Column(name = "subscription_end")
    private LocalDateTime subscriptionEnd;

    @Column(name = "auto_renewal")
    private Boolean autoRenewal = false;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;
}
