package com.kamaldairy.kamal_dairy_backend.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final RazorpayClient razorpayClient;
    // we created the constructor of it
    public PaymentService(RazorpayClient razorpayClient)
    {
        this.razorpayClient = razorpayClient;
    }
    public String createOrder(double amount ) throws Exception {
        JSONObject options = new JSONObject();

        options.put("amount", (int)(amount*100));
        options.put("currency", "INR");
        options.put("receipt","txn_123");

        Order order =razorpayClient.orders.create(options);

        return order.toString();
    }
}
