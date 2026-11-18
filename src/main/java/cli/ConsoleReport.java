package cli;

import core.NotificationRules;
import core.model.OperationType;
import core.model.Wallet;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

public class ConsoleReport {
    private final PrintWriter output;
    private final NotificationRules notificationRules = new NotificationRules();

    ConsoleReport(PrintWriter output) {
        this.output = output;
    }

    void print(Wallet wallet, Set<String> categories) {
        BigDecimal income = wallet.total(OperationType.INCOME, categories);
        BigDecimal expense = wallet.total(OperationType.EXPENSE, categories);

        output.println("Общий доход: " + formatAmount(income));
        output.println("Доходы по категориям:");
        printCategoryTotal(wallet, categories, OperationType.INCOME, income);

        output.println();
        output.println("Общие расходы: " + formatAmount(expense));
        output.println("Расходы по категориям:");
        printCategoryTotal(wallet, categories, OperationType.EXPENSE, expense);

        if (categories.isEmpty()) {
            output.println("Категорий пока нет");
        }

        if (income.signum() == 0 && expense.signum() == 0) {
            output.println("В выбранных категориях пока нет операций");
        }

        output.println();
        output.println("Бюджет по категориям:");
        printCategoryBudgets(wallet, categories);
    }

    private void printCategoryTotal(Wallet wallet, Set<String> categories, OperationType operationType, BigDecimal total) {
        if (total.signum() == 0) {
            output.println("  -");
            return;
        }
        for (String category : categories) {
            BigDecimal amount = wallet.total(operationType, Set.of(category));
            if (amount.signum() > 0) {
                output.printf("  %s: %s%n", category, formatAmount(amount));
            }
        }
    }

    private void printCategoryBudgets(Wallet wallet, Set<String> categories) {
        boolean hasBudget = false;
        for (String category : categories) {
            BigDecimal limit = wallet.getBudget(category);
            if (limit != null) {
                BigDecimal remaining = wallet.remainingBudget(category);
                output.printf("  %s: %s, Оставшийся бюджет: %s%n",
                        category, formatAmount(limit), formatAmount(remaining));
                hasBudget = true;
            }
        }
        if (!hasBudget) {
            output.println("  -");
        }
    }

    void warnBudget(Wallet wallet, String category) {
        switch (notificationRules.budgetWarning(wallet, category)) {
            case EXCEEDED -> printWarning("Бюджет категории '" + category + "' превышен на " +
                    formatAmount(wallet.remainingBudget(category).negate()));
            case NEAR_LIMIT -> printWarning("В категории '" + category + "' использовано " +
                    notificationRules.budgetWarningPercent() + "% бюджета или больше. Остаток: " +
                    formatAmount(wallet.remainingBudget(category)));
            case NONE -> {
            }
        }
    }

    void warnBalance(Wallet wallet) {
        switch (notificationRules.balanceWarning(wallet)) {
            case EXPENSES_EXCEED_INCOME -> printWarning("Расходы превысили доходы");
            case ZERO_BALANCE -> printWarning("Баланс равен нулю");
            case NONE -> {
            }
        }
    }

    private void printWarning(String message) {
        output.println("Оповещение: " + message);
    }

    private String formatAmount(BigDecimal amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }
}
