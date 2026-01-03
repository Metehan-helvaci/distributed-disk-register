package tcp;

import family.MessageId;
import family.StoredMessage;

public class GetCommand implements Command {

    private final String id;

    public GetCommand(String id) {
        this.id = id.trim();
    }
    public String getId() {
        return id;
    }

    @Override
    public String execute(CommandContext context) {

        try {
            MessageId request = MessageId.newBuilder()
                    .setId(Integer.parseInt(id))
                    .build();

            StoredMessage response =
                    context.getStorageStub().retrieve(request);

            return response.getText().isEmpty()
                    ? "NOT_FOUND"
                    : response.getText();

        } catch (Exception e) {
            return "RPC_ERROR";
        }
    }
}

