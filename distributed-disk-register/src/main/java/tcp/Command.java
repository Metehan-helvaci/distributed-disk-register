package tcp;



public interface Command {
    String execute(CommandContext context);
}
