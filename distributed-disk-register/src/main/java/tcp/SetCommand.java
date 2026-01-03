package tcp;

import family.StoreResult;
import family.StoredMessage;

public class SetCommand implements Command {

    private final String id;
    private final String value;

    public SetCommand(String id, String value) {
        this.id = id.trim();
        this.value = value.trim();
    }
    public String getId() {
        return id;
    }

    @Override
    public String execute(CommandContext context) {

        try {
            // Protobuf nesnesi oluştur
            StoredMessage msg = StoredMessage.newBuilder()
                    .setId(Integer.parseInt(id))
                    .setText(value)
                    .build();

            // gRPC çağrısı
            StoreResult result =
                    context.getStorageStub().store(msg);

            return result.getSuccess() ? "OK" : "ERROR";

        } catch (Exception e) {
            return String.valueOf(e);
        }
    }
}

