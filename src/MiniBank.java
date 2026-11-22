import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MiniBank {
    private static final long LIMIT = 1_000_000_000L;

    private static int accountIndex(String account) {
        return switch (account) {
            case "A" -> 0;
            case "B" -> 1;
            default -> -1;
        };
    }

    private static long parseAmount(String text) {
        long amount = 0;
        for (int i = 0; i < text.length(); i++) {
            char digit = text.charAt(i);
            if (digit < '0' || digit > '9') {
                return -1;
            }
            amount = amount * 10 + (digit - '0');
            if (amount > LIMIT) {
                return -1;
            }
        }
        return amount > 0 ? amount : -1;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader input = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        long[] balances = new long[2];
        List<String> history = new ArrayList<>();
        String line;
        while ((line = input.readLine()) != null) {
            String trimmed = line.replaceAll("^[ \\t]+|[ \\t]+$", "");
            String[] parts = trimmed.split("[ \\t]+");
            String command = parts[0];
            int expected = switch (command) {
                case "balance" -> 2;
                case "deposit" -> 3;
                case "transfer" -> 4;
                case "history", "exit" -> 1;
                default -> -1;
            };
            if (parts.length != expected) {
                output.println("ERR_COMMAND");
                continue;
            }
            if (command.equals("exit")) {
                output.println("BYE");
                return;
            }
            if (command.equals("history")) {
                if (history.isEmpty()) {
                    output.println("EMPTY");
                } else {
                    for (String entry : history) {
                        output.println(entry);
                    }
                }
                continue;
            }
            boolean transfer = command.equals("transfer");
            int from = accountIndex(parts[1]);
            int to = transfer ? accountIndex(parts[2]) : from;
            if (from < 0 || to < 0) {
                output.println("ERR_ACCOUNT");
                continue;
            }
            if (command.equals("balance")) {
                output.println("BALANCE " + parts[1] + " " + balances[from]);
                continue;
            }
            long amount = parseAmount(parts[parts.length - 1]);
            if (amount < 0) {
                output.println("ERR_AMOUNT");
                continue;
            }
            if (transfer && from == to) {
                output.println("ERR_SAME_ACCOUNT");
                continue;
            }
            if (transfer && balances[from] < amount) {
                output.println("ERR_FUNDS");
                continue;
            }
            if (balances[to] > LIMIT - amount) {
                output.println("ERR_LIMIT");
                continue;
            }
            if (transfer) {
                balances[from] -= amount;
            }
            balances[to] += amount;
            String entry = transfer
                    ? "TRANSFER " + parts[1] + " " + parts[2] + " " + amount
                    : "DEPOSIT " + parts[1] + " " + amount;
            history.add(entry);
            output.println("OK " + entry);
        }
    }
}
