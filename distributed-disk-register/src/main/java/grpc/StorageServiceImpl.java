package grpc;

import family.MessageId;
import family.StorageServiceGrpc;
import family.StoreResult;
import family.StoredMessage;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.atomic.AtomicInteger;

public class StorageServiceImpl
        extends StorageServiceGrpc.StorageServiceImplBase {
    private static final AtomicInteger storedCount =
            new AtomicInteger(0);
    @Override
    public void store(
            StoredMessage request,
            StreamObserver<StoreResult> responseObserver) {
        try{
            MessageStore.save(request);
            storedCount.incrementAndGet();
            StoreResult result = StoreResult.newBuilder()
                    .setSuccess(true)
                    .setMessage("OK")
                    .build();
            responseObserver.onNext(result);

        }catch (Exception e){
            StoreResult result = StoreResult.newBuilder()
                    .setSuccess(false)
                    .setMessage("FILE_ERROR")
                    .build();
            responseObserver.onNext(result);
        }
        responseObserver.onCompleted();
    }

    @Override
    public void retrieve(
            MessageId request,
            StreamObserver<StoredMessage> responseObserver) {
        try{
            StoredMessage message = MessageStore.load(request.getId());
            responseObserver.onNext(message);


        } catch (Exception e) {
            StoredMessage empty = StoredMessage.newBuilder()
                    .setId(request.getId())
                    .setText("")
                    .build();
            responseObserver.onNext(empty);

        }
        responseObserver.onCompleted();
    }
    public static int getStoredCount() {
        return storedCount.get();
    }
}


