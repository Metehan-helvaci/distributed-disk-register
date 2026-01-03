package tcp;

import family.StorageServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

/*
 * TCP Server:
 * - Komut alır
 * - Parse eder
 * - gRPC StorageService'e yönlendirir
 */
public class TcpServer {

    private static final int TCP_PORT = 5555;
    private static final String GRPC_HOST = "localhost";
    private static final int GRPC_PORT = 9090;

    public static void main(String[] args) throws Exception {

        // gRPC bağlantısı (1 kere oluşturulur)
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(GRPC_HOST, GRPC_PORT)
                .usePlaintext()
                .build();

        StorageServiceGrpc.StorageServiceBlockingStub storageStub =
                StorageServiceGrpc.newBlockingStub(channel);

        CommandContext context = new CommandContext(storageStub);
        CommandParser parser = new CommandParser();

        ServerSocket serverSocket = new ServerSocket(TCP_PORT);
        System.out.println("TCP Server started on port " + TCP_PORT);

        while (true) {
            Socket client = serverSocket.accept();
            new Thread(() -> handleClient(client, parser, context)).start();
        }
    }

    private static void handleClient(
            Socket client,
            CommandParser parser,
            CommandContext context) {

        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(client.getInputStream()));
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(client.getOutputStream()))
        ) {
            String line;

            while ((line = in.readLine()) != null) {

                Command command = parser.parse(line);
                if (command == null) {
                    out.write("INVALID_COMMAND\n");
                    out.flush();
                    continue;
                }

                String result = command.execute(context);
                out.write(result + "\n");
                out.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


