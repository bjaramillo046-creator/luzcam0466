import cl.luzcam.SecureChannel;
import java.nio.file.*;
public class ProtocolClient {
    public static void main(String[] args) throws Exception {
        try (SecureChannel ch = new SecureChannel()) {
            ch.connect("127.0.0.1", args[0]);
            ch.send(Files.readAllBytes(Path.of(args[1])),90);
            ch.send(Files.readAllBytes(Path.of(args[1])),0);
        }
    }
}
