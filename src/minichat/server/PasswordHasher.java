package minichat.server;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Băm mật khẩu bằng PBKDF2WithHmacSHA256 (có sẵn trong JDK) với salt ngẫu nhiên riêng cho từng
 * tài khoản. Server chỉ lưu salt và kết quả băm, không bao giờ lưu mật khẩu gốc.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65_536;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Salt giả dùng khi tên đăng nhập không tồn tại, để thời gian phản hồi giống khi sai mật khẩu. */
    private static final String DUMMY_SALT = newSalt();

    private PasswordHasher() {
    }

    /** Salt mới (Base64). */
    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Kết quả băm (Base64) của mật khẩu với salt cho trước. */
    public static String hash(String password, String salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS);
        try {
            byte[] key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JDK không hỗ trợ " + ALGORITHM, e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Kiểm tra mật khẩu; so sánh với thời gian không đổi để không lộ thông tin qua thời gian. */
    public static boolean verify(String password, String salt, String expectedHash) {
        byte[] actual = Base64.getDecoder().decode(hash(password, salt));
        byte[] expected = Base64.getDecoder().decode(expectedHash);
        return MessageDigest.isEqual(actual, expected);
    }

    /** Tốn thời gian tương đương một lần kiểm tra mật khẩu (dùng khi tên không tồn tại). */
    public static void dummyVerify(String password) {
        hash(password, DUMMY_SALT);
    }
}
