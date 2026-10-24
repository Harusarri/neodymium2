package kr.harusarri.neodymium2.core;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
public final class AtomicFiles {
    public static String read(Path p,int maxBytes)throws IOException {if(Files.size(p)>maxBytes)throw new IOException("File exceeds limit: "+p.getFileName());return Files.readString(p);}
    public static void write(Path target,String text)throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(target.toAbsolutePath().getParent(),"."+target.getFileName(),".tmp");
        try {
            byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){var b=ByteBuffer.wrap(bytes);while(b.hasRemaining())channel.write(b);channel.force(true);}
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temp);}
    }
}
