package com.xeno.crm_backend.controller;


import com.xeno.crm_backend.dto.response.CustomerResponse;
import com.xeno.crm_backend.dto.response.OrderResponse;
import com.xeno.crm_backend.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(customerService.getCustomers(page, size, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderResponse>> getCustomerOrders(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getCustomerOrders(id));
    }
}