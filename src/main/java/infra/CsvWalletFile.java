package infra;

import core.model.Operation;
import core.model.OperationType;
import core.model.Wallet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvWalletFile {
    public static final String HEADER = "type;category;amount;anotherUserLogin";

    public Wallet read(Path path) throws IOException {
        return decode(Files.readString(path));
    }

    public void write(Path path, Wallet wallet) throws IOException {
        Files.writeString(path, encode(wallet));
    }

    static Wallet decode(String content) throws IOException {
        List<String> lines = content.lines().toList();
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
            throw new IOException("Неверный заголовок CSV");
        }

        Wallet wallet = new Wallet();
        for (int index = 1; index < lines.size(); index++) {
            try {
                readRow(wallet, lines.get(index));
            } catch (IllegalArgumentException e) {
                throw new IOException("Некорректная строка CSV под номером " + (index + 1) +
                        ": " + e.getMessage(), e);
            }
        }
        return wallet;
    }

    static String encode(Wallet wallet) {
        StringWriter content = new StringWriter();
        PrintWriter writer = new PrintWriter(content);
        writer.println(HEADER);
        for (String category : wallet.getCategories()) {
            writer.printf("CATEGORY;%s;;%n", category);
            BigDecimal limit = wallet.getBudget(category);
            if (limit != null) {
                writer.printf("BUDGET;%s;%s;%n", category, limit.toPlainString());
            }
        }
        for (Operation operation : wallet.getOperations()) {
            writer.printf("%s;%s;%s;%s%n",
                    operation.getOperationType(),
                    operation.getCategory(),
                    operation.getAmount(),
                    operation.getAnotherUserLogin()
            );
        }
        return content.toString();
    }

    private static void readRow(Wallet wallet, String line) {
        String[] fields = line.split(";", -1);
        if (fields.length != 4) {
            throw new IllegalArgumentException("Ожидается ровно 4 столбца, получено: " + fields.length);
        }
        String rowType = fields[0];
        String category = fields[1];
        String amountText = fields[2];
        String anotherUserLogin = fields[3];
        switch (rowType) {
            case "CATEGORY" -> {
                if (!amountText.isEmpty() || !anotherUserLogin.isEmpty()) {
                    throw new IllegalArgumentException("Для CATEGORY поля amount и anotherUserLogin должны быть пустыми");
                }
                wallet.addCategory(category);
            }
            case "BUDGET" -> {
                if (!anotherUserLogin.isEmpty()) {
                    throw new IllegalArgumentException("Для BUDGET поле anotherUserLogin должно быть пустым");
                }
                wallet.setBudget(category, readAmount(amountText));
            }
            case "INCOME", "EXPENSE" -> {
                if (!anotherUserLogin.isEmpty() && !anotherUserLogin.matches("[a-z0-9_-]{1,30}")) {
                    throw new IllegalArgumentException("Поле anotherUserLogin должно содержать допустимый логин или быть пустым");
                }
                OperationType operationType = OperationType.valueOf(rowType);
                BigDecimal amount = readAmount(amountText);
                wallet.addOperation(new Operation(operationType, category, amount, anotherUserLogin));
            }
            default -> throw new IllegalArgumentException("Неизвестный тип строки: " + rowType);
        }
    }

    private static BigDecimal readAmount(String text) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Поле amount должно содержать число, например 300.50", e);
        }
    }
}
