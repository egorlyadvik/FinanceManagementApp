import core.model.Operation;
import core.model.OperationType;
import core.model.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletTest {
    private final Wallet wallet = new Wallet();

    @BeforeEach
    void createCategories() {
        wallet.addCategory("Еда");
        wallet.addCategory("Такси");
        wallet.addCategory("Зарплата");
    }

    @Test
    void addsIncomeAndExpenses() {
        add(OperationType.INCOME, "Зарплата", "1000");
        add(OperationType.EXPENSE, "Еда", "250");

        assertEquals(new BigDecimal("1000"), wallet.total(OperationType.INCOME, wallet.getCategories()));
        assertEquals(new BigDecimal("250"), wallet.total(OperationType.EXPENSE, wallet.getCategories()));
        assertEquals(new BigDecimal("750"), wallet.balance());
    }

    @Test
    void addsMoneyWithoutRounding() {
        add(OperationType.INCOME, "Зарплата", "0.10");
        add(OperationType.INCOME, "Зарплата", "0.20");

        assertEquals(new BigDecimal("0.30"), wallet.balance());
    }

    @Test
    void totalsOnlySelectedCategories() {
        add(OperationType.EXPENSE, "Еда", "10");
        add(OperationType.EXPENSE, "Такси", "20");
        add(OperationType.EXPENSE, "Зарплата", "30");

        assertEquals(new BigDecimal("30"), wallet.total(OperationType.EXPENSE, Set.of("Еда", "Такси")));
    }

    @Test
    void rejectsUnknownCategoryInReport() {
        assertThrows(IllegalArgumentException.class,
                () -> wallet.total(OperationType.INCOME, Set.of("Еда", "Одежда")));
    }

    @Test
    void countsOnlyExpensesAgainstBudget() {
        wallet.setBudget("Еда", new BigDecimal("100"));
        add(OperationType.INCOME, "Еда", "20");
        add(OperationType.EXPENSE, "Еда", "130");

        assertEquals(new BigDecimal("-30"), wallet.remainingBudget("Еда"));
    }

    @Test
    void updatesBudgetForExistingExpenses() {
        wallet.setBudget("Еда", new BigDecimal("100"));
        add(OperationType.EXPENSE, "Еда", "80");
        wallet.setBudget("Еда", new BigDecimal("150"));

        assertEquals(new BigDecimal("70"), wallet.remainingBudget("Еда"));
    }

    @Test
    void renamesCategoryWithHistoryAndBudget() {
        wallet.setBudget("Еда", new BigDecimal("100"));
        add(OperationType.EXPENSE, "Еда", "20");
        wallet.renameCategory("Еда", "Продукты");

        assertFalse(wallet.getCategories().contains("Еда"));
        assertEquals("Продукты", wallet.getOperations().get(0).getCategory());
        assertEquals(new BigDecimal("80"), wallet.remainingBudget("Продукты"));
    }

    @Test
    void rejectsInvalidCategoryNames() {
        assertThrows(IllegalArgumentException.class, () -> wallet.addCategory(" "));
        assertThrows(IllegalArgumentException.class, () -> wallet.addCategory("Еда;Такси"));
        assertThrows(IllegalArgumentException.class, () -> wallet.addCategory("Еда \"дома\""));
        assertThrows(IllegalArgumentException.class, () -> wallet.addCategory("Еда\rдома"));
        assertThrows(IllegalArgumentException.class, () -> wallet.addCategory("Еда\nдома"));
        assertThrows(IllegalArgumentException.class, () -> wallet.addCategory("Еда"));
        assertThrows(IllegalArgumentException.class, () -> wallet.renameCategory("Еда", "Такси"));
        assertEquals(Set.of("Еда", "Такси", "Зарплата"), wallet.getCategories());
    }

    @Test
    void rejectsInvalidAmountsAndMissingCategory() {
        assertThrows(IllegalArgumentException.class, () -> add(OperationType.EXPENSE, "Еда", "0"));
        assertThrows(IllegalArgumentException.class, () -> add(OperationType.INCOME, "Еда", "0.001"));
        assertThrows(IllegalArgumentException.class, () -> add(OperationType.EXPENSE, "Кафе", "1"));
        assertThrows(IllegalArgumentException.class, () -> wallet.setBudget("Еда", new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> wallet.setBudget("Еда", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> wallet.setBudget("Еда", new BigDecimal("0.001")));
        assertEquals(BigDecimal.ZERO, wallet.balance());
    }

    private void add(OperationType type, String category, String amount) {
        wallet.addOperation(new Operation(type, category, new BigDecimal(amount), ""));
    }
}
