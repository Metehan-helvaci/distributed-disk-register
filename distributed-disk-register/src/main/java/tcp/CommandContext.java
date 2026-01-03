package tcp;

import family.StorageServiceGrpc;

/*
 * TCP katmanı için context:
 * - Artık veri tutmaz
 * - Sadece gRPC stub taşır
 */
public class CommandContext {

    // gRPC client (storage servisine bağlanır)
    private final StorageServiceGrpc.StorageServiceBlockingStub storageStub;

    public CommandContext(
            StorageServiceGrpc.StorageServiceBlockingStub storageStub) {
        this.storageStub = storageStub;
    }

    public StorageServiceGrpc.StorageServiceBlockingStub getStorageStub() {
        return storageStub;
    }
}
