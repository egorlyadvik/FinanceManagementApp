import cli.ConsoleApp;
import core.FinanceService;
import infra.CsvWalletFile;
import infra.FileFinanceStorage;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Scanner;

public final class Main {
    public static void main(String[] args) throws IOException {
        try (Scanner input = new Scanner(System.in)) {
            FinanceService finance = new FinanceService(new FileFinanceStorage(Path.of("data")));
            PrintWriter output = new PrintWriter(System.out);
            ConsoleApp consoleApp = new ConsoleApp(finance, new CsvWalletFile(), input, output);
            consoleApp.run();
        }
    }
}
