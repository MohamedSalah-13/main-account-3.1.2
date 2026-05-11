package com.hamza.account.model.domain;

import com.hamza.account.type.TreasuryMovementType;
import com.hamza.account.type.TreasuryReferenceType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TreasuryMovement {

    private long id;

    private Treasury treasury;

    private LocalDate movementDate;

    private TreasuryMovementType movementType;

    private BigDecimal amountIn = BigDecimal.ZERO;

    private BigDecimal amountOut = BigDecimal.ZERO;

    private BigDecimal balanceAfter = BigDecimal.ZERO;

    private TreasuryReferenceType referenceType;

    private Long referenceId;

    private String notes;

    private LocalDateTime dateInsert;

    private LocalDateTime updatedAt;

    private int userId;

    public BigDecimal getMovementAmount() {
        if (amountIn != null && amountIn.compareTo(BigDecimal.ZERO) > 0) {
            return amountIn;
        }

        if (amountOut != null && amountOut.compareTo(BigDecimal.ZERO) > 0) {
            return amountOut;
        }

        return BigDecimal.ZERO;
    }

    public boolean isInMovement() {
        return amountIn != null && amountIn.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isOutMovement() {
        return amountOut != null && amountOut.compareTo(BigDecimal.ZERO) > 0;
    }
}