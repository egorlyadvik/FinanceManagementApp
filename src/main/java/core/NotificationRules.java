package core;

import core.model.Wallet;

import java.math.BigDecimal;

public final class NotificationRules {
    private static final BigDecimal WARNING_LEVEL = new BigDecimal("0.80");

    public enum BudgetWarning {
        NONE,
        NEAR_LIMIT,
        EXCEEDED
    }

    public enum BalanceWarning {
        NONE,
        EXPENSES_EXCEED_INCOME,
        ZERO_BALANCE
    }

    public BudgetWarning budgetWarning(Wallet wallet, String category) {
        BigDecimal limit = wallet.getBudget(category);
        if (limit == null) {
            return BudgetWarning.NONE;
        }
        BigDecimal remaining = wallet.remainingBudget(category);
        if (remaining.signum() < 0) {
            return BudgetWarning.EXCEEDED;
        }
        if (limit.signum() > 0 && limit.subtract(remaining).compareTo(limit.multiply(WARNING_LEVEL)) >= 0) {
            return BudgetWarning.NEAR_LIMIT;
        }
        return BudgetWarning.NONE;
    }

    public BalanceWarning balanceWarning(Wallet wallet) {
        int sign = wallet.balance().signum();
        if (sign < 0) {
            return BalanceWarning.EXPENSES_EXCEED_INCOME;
        }
        if (sign == 0) {
            return BalanceWarning.ZERO_BALANCE;
        }
        return BalanceWarning.NONE;
    }

    public BigDecimal budgetWarningPercent() {
        return WARNING_LEVEL.movePointRight(2);
    }
}
