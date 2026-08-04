package com.example.service;

import com.example.shared.OrderId;

public class OrderService {
    public OrderId create(String value) {
        return new OrderId(value);
    }
}
