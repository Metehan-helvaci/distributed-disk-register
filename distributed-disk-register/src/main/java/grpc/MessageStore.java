package grpc;

import family.StoredMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/*
 * Disk ile ilgili tüm işleri yapan sınıf
 */
public class MessageStore {

    private static  String BASE_DIR = "messages";

    public static void setBaseDir(String dir) {
        BASE_DIR = dir;
    }

    /*
     * Mesajı diske yazar
     * messages/{id}.msg
     */
    public static void save(StoredMessage message) throws IOException {

        Path dir = Paths.get(BASE_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

// messages/1.msg
        Path file = dir.resolve(message.getId() + ".msg");

// geçici dosya (atomic write için)
        Path tmp = dir.resolve(message.getId() + ".tmp");

// 1️⃣ Önce tmp'ye yaz (UTF-8 net)
        Files.writeString(
                tmp,
                message.getText(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

// 2️⃣ Atomic move ile gerçek dosya adına taşı
        Files.move(
                tmp,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

        /*
     * Mesajı diskten okur
     */
    public static StoredMessage load(int id) throws IOException {

        Path file = Paths.get(BASE_DIR, id + ".msg");

        if (!Files.exists(file)) {
            return StoredMessage.newBuilder()
                    .setId(id)
                    .setText("")
                    .build();
        }

        String text = Files.readString(file);

        return StoredMessage.newBuilder()
                .setId(id)
                .setText(text)
                .build();
    }
    public static boolean exists(int id) {
        Path file = Paths.get(BASE_DIR, id + ".msg");
        return Files.exists(file);
    }
}
