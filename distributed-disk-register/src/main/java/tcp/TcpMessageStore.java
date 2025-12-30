package tcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class TcpMessageStore {

    public static void saveMessages(String context, String id) throws IOException {

        // messages klasörü yoksa oluştur
        Path folder = Paths.get("distributed-disk-register/src/messages");
        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
        }

        // Dosya ismi: id.msg
        String fileName = id + ".msg";
        Path filePath = folder.resolve(fileName);



        // Dosyaya yazılır (varsa **üzerine yazar**)
        Files.writeString(filePath, context, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("Mesaj yazıldı → " + filePath);
    }
    public static String readFile(String id) {
        try {
            Path filepath = Paths.get("distributed-disk-register/src/messages/"+ id +".msg");

            if (!Files.exists(filepath)) {
                return "NOT_FOUND";
            }

            String content = Files.readString(filepath);
            return content;
        }
        catch (IOException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }
}


