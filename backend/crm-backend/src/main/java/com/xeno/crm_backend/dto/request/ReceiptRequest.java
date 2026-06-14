package com.xeno.crm_backend.dto.request;


import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ReceiptRequest {
    private UUID logId;
    private String status;  // delivered, failed, opened, read, clicked
    private BigDecimal orderValue;
}
