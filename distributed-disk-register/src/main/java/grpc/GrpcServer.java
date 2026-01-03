package grpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;

/*
 * gRPC Server
 * FamilyService + StorageService aynı process içinde
 */
public class GrpcServer {

    public static void main(String[] args) throws Exception {

        Server server = ServerBuilder
                .forPort(9090)
                .addService(new StorageServiceImpl())
                // FamilyService henüz yazılmadıysa eklemiyoruz
                // .addService(new FamilyServiceImpl())
                .build()
                .start();



        System.out.println("gRPC Server started on port 9090");

        server.awaitTermination();
    }
}