package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.service.PaymentService;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("api/payment")
public class PaymentController {
    // this class will basically handle the http request for the payment page
     private final PaymentService paymentService;
     public PaymentController(PaymentService paymentService)
     {
         this.paymentService = paymentService;
     }
     @PostMapping("/create-order")
     public String createOrder(@RequestParam double amount ) throws Exception{
         return paymentService.createOrder(amount);
     }
}
