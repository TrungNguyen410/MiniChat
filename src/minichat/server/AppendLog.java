package minichat.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

/**
 * Một file nhật ký chỉ ghi nối thêm (append-only): mỗi dòng là một bản ghi UTF-8.
 *
 * - Ghi: nối thêm một dòng rồi flush ngay, không bao giờ ghi đè dữ liệu cũ, nên server tắt đột ngột
 *   cũng chỉ có thể làm hỏng dòng cuối cùng.
 * - Đọc: đọc lần lượt từng dòng khi khởi động; dòng nào xử lý lỗi thì bỏ qua và ghi cảnh báo.
 */
final class AppendLog implements AutoCloseable {

    /** Xử lý một dòng khi nạp lại; ném ngoại lệ bất kỳ nếu dòng sai định dạng. */
    interface LineHandler {
        void handle(String[] fields, String line) throws Exception;
    }

    private final Path path;
    private final Consumer<String> logger;
    private Writer writer;

    AppendLog(Path path, Consumer<String> logger) {
        this.path = path;
        this.logger = logger;
    }

    /**
     * Đọc lại toàn bộ file. Mỗi dòng được tách thành tối đa {@code maxFields} trường
     * (trường cuối giữ nguyên khoảng trắng). Trả về số dòng hỏng đã bỏ qua.
     */
    int load(int maxFields, LineHandler handler) throws IOException {
        if (!Files.exists(path)) {
            return 0;
        }
        int bad = 0;
        int lineNo = 0;
        try (BufferedReader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                lineNo++;
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    handler.handle(line.split(" ", maxFields), line);
                } catch (Exception e) {
                    bad++;
                    logger.accept("Bỏ qua dòng hỏng ở " + path.getFileName() + ":" + lineNo + " (" + e + ")");
                }
            }
        }
        return bad;
    }

    /** Nối thêm một dòng vào cuối file và đẩy xuống đĩa ngay. */
    synchronized void append(String line) throws IOException {
        if (writer == null) {
            open();
        }
        writer.write(line.replace('\r', ' ').replace('\n', ' '));
        writer.write('\n');
        writer.flush();
    }

    private void open() throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        boolean needNewline = false;
        if (Files.exists(path) && Files.size(path) > 0) {
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                raf.seek(raf.length() - 1);
                needNewline = raf.read() != '\n'; // dòng cuối bị ghi dở: tách dòng mới ra khỏi nó
            }
        }
        writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND), StandardCharsets.UTF_8));
        if (needNewline) {
            writer.write('\n');
            writer.flush();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
