package grpc;

import family.MessageId;
import family.StorageServiceGrpc;
import family.StoreResult;
import family.StoredMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class GrpcTestClient {

    public static void main(String[] args) {

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        //senkron bağlantı sağlanır
        //Bu satırlar olmazsa rpc çağrısı yapılmaz,grpc client oluşmaz,servera erişilmez
        StorageServiceGrpc.StorageServiceBlockingStub stub =
                StorageServiceGrpc.newBlockingStub(channel);

        // STORE
        StoredMessage msg = StoredMessage.newBuilder()
                .setId(1)
                .setText("Merhaba gRPC")
                .build();

        StoreResult result = stub.store(msg);
        System.out.println(result.getMessage());

        // RETRIEVE
        MessageId id = MessageId.newBuilder()
                .setId(1)
                .build();

        StoredMessage response = stub.retrieve(id);
        System.out.println("Gelen mesaj: " + response.getText());

        channel.shutdown();
    }
}
