package core.model;

import java.math.BigDecimal;
import java.util.*;

public final class Wallet {
    public static final String TRANSFER_CATEGORY = "Переводы";

    private final Map<String, BigDecimal> categories = new LinkedHashMap<>();
    private final List<Operation> operations = new ArrayList<>();

    public void addCategory(String category) {
        validateNewCategory(category);
        categories.put(category, null);
    }

    public void renameCategory(String oldName, String newName) {
        validateExistingCategory(oldName);
        if (oldName.equals(newName)) {
            return;
        }
        if (TRANSFER_CATEGORY.equals(oldName)) {
            throw new IllegalArgumentException("Нельзя переименовать категорию: " + TRANSFER_CATEGORY);
        }
        validateNewCategory(newName);
        categories.put(newName, categories.remove(oldName));
        for (int i = 0; i < operations.size(); i++) {
            Operation operation = operations.get(i);
            if (operation.getCategory().equals(oldName)) {
                operations.set(i, new Operation(
                        operation.getOperationType(), newName, operation.getAmount(), operation.getAnotherUserLogin()));
            }
        }
    }

    public void addOperation(Operation operation) {
        validateExistingCategory(operation.getCategory());
        validatePositiveAmount(operation.getAmount());
        operations.add(operation);
    }

    public void setBudget(String category, BigDecimal limit) {
        validateExistingCategory(category);
        validatePositiveAmount(limit);
        categories.put(category, limit);
    }

    public Set<String> getCategories() {
        return Collections.unmodifiableSet(categories.keySet());
    }

    public BigDecimal getBudget(String category) {
        validateExistingCategory(category);
        return categories.get(category);
    }

    public List<Operation> getOperations() {
        return Collections.unmodifiableList(operations);
    }

    public BigDecimal total(OperationType type, Set<String> selectedCategories) {
        selectedCategories.forEach(this::validateExistingCategory);
        BigDecimal sum = BigDecimal.ZERO;
        for (Operation operation : operations) {
            if (operation.getOperationType() == type && selectedCategories.contains(operation.getCategory())) {
                sum = sum.add(operation.getAmount());
            }
        }
        return sum;
    }

    public BigDecimal balance() {
        Set<String> allCategories = getCategories();
        BigDecimal income = total(OperationType.INCOME, allCategories);
        BigDecimal expense = total(OperationType.EXPENSE, allCategories);
        return income.subtract(expense);
    }

    public BigDecimal remainingBudget(String category) {
        BigDecimal limit = getBudget(category);
        return limit == null ? null : limit.subtract(total(OperationType.EXPENSE, Set.of(category)));
    }

    public static void validatePositiveAmount(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Значение должно быть положительным, до двух знаков после запятой");
        }
    }

    public void validateExistingCategory(String category) {
        if (!categories.containsKey(category)) {
            throw new IllegalArgumentException("Категория не найдена: " + category);
        }
    }

    private void validateNewCategory(String category) {
        if (category.isBlank() || category.matches("(?s).*[;\"\r\n].*")) {
            throw new IllegalArgumentException("Введите название категории без точки с запятой, кавычек и переносов");
        }
        if (categories.containsKey(category)) {
            throw new IllegalArgumentException("Категория уже существует");
        }
    }
}
