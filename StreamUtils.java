import java.io.IOException;
import java.io.InputStream;

public class StreamUtils {

    // Read text line by line from raw InputStream
    public static String readLineFromStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                in.read(); // Read and skip the '\n' character
                break;
            } else if (b == '\n') {
                break;
            }
            sb.append((char) b);
        }
        if (sb.length() == 0 && b == -1) return null;
        return sb.toString();
    }

    // Read exactly the specified number of bytes from InputStream
    public static void readExactly(InputStream in, byte[] buffer, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int bytesRead = in.read(buffer, totalRead, length - totalRead);
            if (bytesRead == -1) break;
            totalRead += bytesRead;
        }
    }

    // Search for a specific byte pattern within a byte array
    public static int indexOf(byte[] data, byte[] pattern, int start) {
        for (int i = start; i <= data.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }
}