package utils;
import java.security.MessageDigest;

public class PasswordUtil {
    public static String md5(String input) {
        try {
            var md = MessageDigest.getInstance("MD5");
            var b = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
