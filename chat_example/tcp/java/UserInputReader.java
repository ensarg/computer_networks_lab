/**
 * UserInputReader.java
 * Prompts the user for a display name on stdin.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

class UserInputReader {

    String readUsername() throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter your name: ");
        String name = stdin.readLine().trim();
        return name.isEmpty() ? "anonymous" : name;
    }
}
