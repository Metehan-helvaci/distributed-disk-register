package grpc;

import family.StoredMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/*
 * Disk ile ilgili tüm işleri yapan sınıf
 */
public class MessageStore {

    private static final String BASE_DIR = "messages";

    /*
     * Mesajı diske yazar
     * messages/{id}.msg
     */
    public static void save(StoredMessage message) throws IOException {

        // messages klasörü yoksa oluştur
        Path dir = Paths.get(BASE_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        // Dosya yolu: messages/1.msg
        Path file = dir.resolve(message.getId() + ".msg");

        // Sadece text yazıyoruz
        Files.writeString(file, message.getText());
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
