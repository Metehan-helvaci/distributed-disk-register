package tcp;

public class CommandParser {
    public static Command parse(String line){
        if(line == null || line.isBlank()) {
            return null;
        }
        line.trim();
        String[] parts = line.split(" ",3);
        String cmd = parts[0].toUpperCase();

        if (cmd.equals("SET")){return parts.length < 3 ? null :  new SetCommand(parts[1],parts[2]);}
        else if(cmd.equals("GET")){return parts.length < 2 ? null : new GetCommand(parts[1]);}
        else{return null;}

    }
}
