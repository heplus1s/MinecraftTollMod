package dev.jdguo.toll;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

public final class TollStore {
    public static final class Point {
        public String id=UUID.randomUUID().toString(), name,dimension,currency,currencyName,recipient,amount;
        public double x,y,z,radius,height=4;
        public Geometry.Position center(){return new Geometry.Position(x,y,z);}
    }
    public static final class Order {
        public String reference="toll:"+UUID.randomUUID(),player,recipient,currency,amount,point,boat,status="PENDING",lastCode="";
    }
    public static final class Seizure {
        public String boat,dimension,entityType,nbt,reason;
        public String status="ARCHIVED";
    }
    public int schema=1;
    public Map<String,Point> points=new LinkedHashMap<>();
    public Map<String,Order> orders=new LinkedHashMap<>();
    public Map<String,Seizure> seizures=new LinkedHashMap<>();
    public Set<String> blocked=new HashSet<>();
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static TollStore load(Path path)throws IOException {
        if(!Files.exists(path))return new TollStore();
        try {
            TollStore s=GSON.fromJson(Files.readString(path),TollStore.class);
            if(s==null||s.schema!=1||s.points==null||s.orders==null||s.blocked==null||s.seizures==null)throw new IllegalStateException("schema");
            for(var entry:s.points.entrySet()) {
                Point p=Objects.requireNonNull(entry.getValue());
                UUID.fromString(p.id);UUID.fromString(p.recipient);
                if(!entry.getKey().equals(p.name)||p.name.isBlank()||p.dimension==null||p.currency==null||p.currencyName==null||new java.math.BigInteger(p.amount).signum()<=0
                  ||!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||!Double.isFinite(p.radius)||p.radius<1||p.radius>128||!Double.isFinite(p.height)||p.height<=0)throw new IllegalStateException("point");
            }
            for(var entry:s.orders.entrySet()) {
                Order o=Objects.requireNonNull(entry.getValue());UUID.fromString(o.player);UUID.fromString(o.recipient);UUID.fromString(o.point);
                if(o.boat!=null)UUID.fromString(o.boat);
                if(!entry.getKey().equals(o.reference)||!o.reference.startsWith("toll:")||o.currency==null||new java.math.BigInteger(o.amount).signum()<=0||!Set.of("PENDING","PAID","INSUFFICIENT","CONFISCATED","REVIEW").contains(o.status))throw new IllegalStateException("order");
            }
            for(var entry:s.seizures.entrySet()) {
                Seizure z=Objects.requireNonNull(entry.getValue());UUID.fromString(z.boat);
                if(!entry.getKey().equals(z.boat)||z.nbt==null||z.dimension==null||z.entityType==null||z.reason==null||!Set.of("ARCHIVED","REMOVED").contains(z.status))throw new IllegalStateException("seizure");
            }
            return s;
        }catch(RuntimeException e){throw new IOException("Invalid toll save; refusing to replace it",e);}
    }
    public void save(Path path)throws IOException {
        Files.createDirectories(path.getParent());
        Path temp=path.resolveSibling(path.getFileName()+".tmp");
        byte[] bytes=GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
        try(FileChannel ch=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
            ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())ch.write(b);ch.force(true);
        }
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}
