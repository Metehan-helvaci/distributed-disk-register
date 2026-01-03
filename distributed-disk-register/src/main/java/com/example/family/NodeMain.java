package com.example.family;

import grpc.StorageServiceImpl;
import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import family.ChatMessage;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import tcp.*;

import java.io.*;
import java.net.Socket;


import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class NodeMain {

    private static final int START_PORT = 5555;
    private static final int PRINT_INTERVAL_SECONDS = 10;
    private static final AtomicInteger rrIndex = new AtomicInteger(0);

    private static final Map<String, List<NodeInfo>> messageLocations =
            new ConcurrentHashMap<>();

    private static int TOLERANCE = 1;

    public static void main(String[] args) throws Exception {
        TOLERANCE = ToleranceConfig.loadTolerance() ;

        String host = "127.0.0.1";
        int port = findFreePort(START_PORT);
        //Kendi node bilgisini oluşturur.
        NodeInfo self = NodeInfo.newBuilder()
                .setHost(host)
                .setPort(port)
                .build();

        NodeRegistry registry = new NodeRegistry();
        FamilyServiceImpl service = new FamilyServiceImpl(registry, self);
        //grpc sunucusunu başlatır
        Server server = ServerBuilder
                .forPort(port)
                .addService(service)
                .addService(new StorageServiceImpl())
                .build()
                .start();

        System.out.printf("Node started on %s:%d%n", host, port,TOLERANCE);

        CommandParser parser = new CommandParser();



        // Eğer bu ilk node ise (port 5555), TCP 6666'da text dinlesin
        if (port == START_PORT) {
            startLeaderTextListener(registry, self,parser);
            printStorageStats(self);
            startMessageDistributionPrinter();
        }

        discoverExistingNodes(host, port, registry, self);
        startFamilyPrinter(registry, self);
        startHealthChecker(registry, self);


        server.awaitTermination();




    }
    //bu function lider ile 6666 portu arasındaki
    private static void startLeaderTextListener(NodeRegistry registry,
                                                NodeInfo self,
                                                CommandParser parser) {
        // Sadece lider (5555 portlu node) bu methodu çağırmalı
        new Thread(() -> {
            //ServerSocket sunucu soketi, 6666 portundaki bağlantıları dinler
            try (ServerSocket serverSocket = new ServerSocket(6666)) {
                System.out.printf("Leader listening for text on TCP %s:%d%n",
                        self.getHost(), 6666);

                while (true) {
                    //accept(): Bloklayıcı metod yeni bağlantı gelene kadar bekler
                    Socket client = serverSocket.accept();
                    //Her yeni bağlantı için ayrı thread oluşturulur
                    new Thread(() -> handleClientTextConnection(client, registry, self,parser)).start();
                }

            } catch (IOException e) {
                System.err.println("Error in leader text listener: " + e.getMessage());
            }
        }, "LeaderTextListener").start(); //Baştaki threade isim verir
    }

    private static void handleClientTextConnection(Socket client,
                                                   NodeRegistry registry,
                                                   NodeInfo self,
                                                   CommandParser parser) {
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream()));
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(client.getOutputStream()))
        ) {
            String line;

            while ((line = reader.readLine()) != null) {

                Command command = parser.parse(line);
                if (command == null) {
                    out.write("INVALID_COMMAND\n");
                    out.flush();
                    continue;
                }

                List<NodeInfo> aliveNodes = registry.snapshot()
                        .stream()
                        .filter(n -> !n.equals(self))
                        .toList();

                List<NodeInfo> replicas =
                        selectReplicas(aliveNodes, TOLERANCE);


                // 🔹 LEADER CHANNEL + CONTEXT
                ManagedChannel leaderChannel = ManagedChannelBuilder
                        .forAddress(self.getHost(), self.getPort())
                        .usePlaintext()
                        .build();

                var leaderStub =
                        family.StorageServiceGrpc.newBlockingStub(leaderChannel);
                CommandContext leaderContext =
                        new CommandContext(leaderStub);

                // 🟡 GET → SADECE LEADER
                if (command instanceof GetCommand) {
                    String result = command.execute(leaderContext);
                    out.write(result + "\n");
                    out.flush();
                    leaderChannel.shutdownNow();
                    continue;
                }

                // 🟢 SET → ÖNCE REPLICAS
                boolean allOk = true;
                String messageId = null;
                List<NodeInfo> writtenNodes = new ArrayList<>();

                for (NodeInfo n : replicas) {
                    try {
                        ManagedChannel channel = ManagedChannelBuilder
                                .forAddress(n.getHost(), n.getPort())
                                .usePlaintext()
                                .build();

                        var storageStub =
                                family.StorageServiceGrpc.newBlockingStub(channel)
                                        .withDeadlineAfter(300, TimeUnit.MILLISECONDS);

                        CommandContext context =
                                new CommandContext(storageStub);

                        String result = command.execute(context);

                        if (!"OK".equals(result)) {
                            allOk = false;
                        } else {
                            writtenNodes.add(n);
                            if (command instanceof SetCommand setCmd) {
                                messageId = setCmd.getId();
                            }
                        }

                        channel.shutdownNow();

                    } catch (Exception e) {
                        registry.remove(n);
                        allOk = false;
                    }
                }

                // ❌ REPLICA’LARDAN BİRİ FAIL → LEADER YAZMAZ
                if (!allOk) {
                    out.write("ERROR\n");
                    out.flush();
                    leaderChannel.shutdownNow();
                    continue;
                }

                // 🔥 HER ŞEY OK → LEADER YAZAR
                String leaderResult = command.execute(leaderContext);
                out.write(leaderResult + "\n");

                if (messageId != null) {
                    messageLocations.put(messageId, writtenNodes);
                }

                out.flush();
                leaderChannel.shutdownNow();

                // (Opsiyonel log / broadcast kısmı aynen kalabilir)
                String text = line.trim();
                if (text.isEmpty()) continue;

                long ts = System.currentTimeMillis();

                System.out.println("📝 Received from TCP: " + text);

                ChatMessage msg = ChatMessage.newBuilder()
                        .setText(text)
                        .setFromHost(self.getHost())
                        .setFromPort(self.getPort())
                        .setTimestamp(ts)
                        .build();

                broadcastToFamily(registry, self, msg);
            }

        } catch (IOException e) {
            System.err.println("TCP client handler error: " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }




    private static void broadcastToFamily(NodeRegistry registry,
                                          NodeInfo self,
                                          ChatMessage msg) {

        List<NodeInfo> members = registry.snapshot();

        for (NodeInfo n : members) {
            // Kendimize tekrar gönderme!
            if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) {
                continue;
            }

            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(n.getHost(), n.getPort())
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);



                stub.receiveChat(msg);

                System.out.printf("Broadcasted message to %s:%d%n", n.getHost(), n.getPort());

            } catch (Exception e) {
                System.err.printf("Failed to send to %s:%d (%s)%n",
                        n.getHost(), n.getPort(), e.getMessage());
            } finally {
                if (channel != null) channel.shutdownNow();
            }
        }
    }


    private static int findFreePort(int startPort) {
        int port = startPort;
        while (true) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                port++;
            }
        }
    }
    //
    private static void discoverExistingNodes(String host,
                                              int selfPort,
                                              NodeRegistry registry,
                                              NodeInfo self) {

        for (int port = START_PORT; port < selfPort; port++) {
            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(host, port)
                        .usePlaintext()
                        .build();
// stub , Sunucuda çalışan fonksiyonlara
//istemciden, sanki lokal fonksiyon çağırır gibi ulaşmanı sağlar.protodaki join rpcsini çalıştırır.
                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);

                FamilyView view = stub.join(self);
                registry.addAll(view.getMembersList());

                System.out.printf("Joined through %s:%d, family size now: %d%n",
                        host, port, registry.snapshot().size());

            } catch (Exception ignored) {
            } finally {
                if (channel != null) channel.shutdownNow();
            }
        }
    }

    private static void startFamilyPrinter(NodeRegistry registry, NodeInfo self) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
//tek threadde çalışcak 10 saniyede bir çalışcak. Her tetiklendiğinde aşağıdaki işleri yapar.
        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();
            System.out.println("======================================");
            System.out.printf("Family at %s:%d (me)%n", self.getHost(), self.getPort());
            System.out.println("Time: " + LocalDateTime.now());
            System.out.println("Members:");

            for (NodeInfo n : members) {
                boolean isMe = n.getHost().equals(self.getHost()) && n.getPort() == self.getPort();
                System.out.printf(" - %s:%d%s%n",
                        n.getHost(),
                        n.getPort(),
                        isMe ? " (me)" : "");
            }
            System.out.println("======================================");
        }, 3, PRINT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static void startHealthChecker(NodeRegistry registry, NodeInfo self) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();

            for (NodeInfo n : members) {
                // Kendimizi kontrol etmeyelim
                if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) {
                    continue;
                }

                ManagedChannel channel = null;
                try {
                    channel = ManagedChannelBuilder
                            .forAddress(n.getHost(), n.getPort())
                            .usePlaintext()
                            .build();

                    FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                            FamilyServiceGrpc.newBlockingStub(channel);

                    // Ping gibi kullanıyoruz: cevap bizi ilgilendirmiyor,
                    // sadece RPC'nin hata fırlatmaması önemli.
                    stub.getFamily(Empty.newBuilder().build());

                } catch (Exception e) {
                    // Bağlantı yok / node ölmüş → listeden çıkar
                    System.out.printf("Node %s:%d unreachable, removing from family%n",
                            n.getHost(), n.getPort());
                    registry.remove(n);
                } finally {
                    if (channel != null) {
                        channel.shutdownNow();
                    }
                }
            }

        }, 5, 10, TimeUnit.SECONDS); // 5 sn sonra başla, 10 sn'de bir kontrol et
    }
    private static List<NodeInfo> selectReplicas(
            List<NodeInfo> nodes, int tolerance) {

        List<NodeInfo> result = new ArrayList<>();
        int size = nodes.size();
        if (size == 0) return result;

        int start = rrIndex.getAndIncrement();

        for (int i = 0; i < tolerance; i++) {
            NodeInfo n = nodes.get((start + i) % size);
            result.add(n);
        }
        return result;
    }
    private static void printStorageStats(NodeInfo self) {

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10_000);

                    System.out.println("📊 STORAGE STATS");
                    System.out.println(
                            self.getHost() + ":" + self.getPort()
                                    + " -> "
                                    + StorageServiceImpl.getStoredCount()
                    );

                } catch (InterruptedException ignored) {}
            }
        }, "StorageStatsPrinter").start();
    }

    private static void printMessageDistribution() {

        Map<NodeInfo, Integer> counts = new HashMap<>();

        for (List<NodeInfo> nodes : messageLocations.values()) {
            for (NodeInfo n : nodes) {
                counts.merge(n, 1, Integer::sum);
            }
        }

        System.out.println("📦 MESSAGE DISTRIBUTION (node -> message count)");

        if (counts.isEmpty()) {
            System.out.println(" (no messages yet)");
            return;
        }

        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> {
                    NodeInfo n = e.getKey();
                    System.out.printf(
                            " - %s:%d -> %d mesaj%n",
                            n.getHost(),
                            n.getPort(),
                            e.getValue()
                    );
                });
    }
    private static void startMessageDistributionPrinter() {

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("======================================");
            printMessageDistribution();
            System.out.println("======================================");
        }, 5, PRINT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }




}
