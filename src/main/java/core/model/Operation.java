package core.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class Operation {
    private final OperationType operationType;
    private final String category;
    private final BigDecimal amount;
    private final String anotherUserLogin;

    public Operation(OperationType operationType, String category, BigDecimal amount, String anotherUserLogin) {
        this.operationType = operationType;
        this.category = category;
        this.amount = amount;
        this.anotherUserLogin = anotherUserLogin;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getAnotherUserLogin() {
        return anotherUserLogin;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Operation operation)) {
            return false;
        }
        return operationType == operation.operationType
                && Objects.equals(category, operation.category)
                && Objects.equals(amount, operation.amount)
                && Objects.equals(anotherUserLogin, operation.anotherUserLogin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, category, amount, anotherUserLogin);
    }
}
