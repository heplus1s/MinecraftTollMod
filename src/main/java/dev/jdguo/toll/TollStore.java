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
        public String mode="legacy", owner;
        public double x,y,z,radius,height=4;
        public Double below,above;
        public double below(){return below==null?height:below;}
        public double above(){return above==null?height:above;}
        public double halfHeight(){return (below()+above())/2;}
        public Geometry.Position center(){return new Geometry.Position(x,y+(above()-below())/2,z);}
    }
    public static final class Order {
        public String reference="toll:"+UUID.randomUUID(),player,recipient,currency,amount,point,boat,status="PENDING",lastCode="";
        public String mode="legacy";
        public String transport="legacy"; // missing field means original MarketSettlement protocol
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
        if(Files.size(path)>64L*1024*1024)throw new IOException("Toll save exceeds 64 MiB safety limit");
        return fromJson(Files.readString(path));
    }
    public String toJson(){return GSON.toJson(this);}
    public static TollStore fromJson(String json)throws IOException {
        if(json==null||json.length()>64*1024*1024)throw new IOException("Toll state exceeds safety limit");
        try {
            TollStore s=GSON.fromJson(json,TollStore.class);
            if(s==null||s.schema!=1||s.points==null||s.orders==null||s.blocked==null||s.seizures==null)throw new IllegalStateException("schema");
            if(s.points.size()>128||s.orders.size()>10000||s.blocked.size()>100000)throw new IllegalStateException("size limit");
            for(var entry:s.points.entrySet()) {
                Point p=Objects.requireNonNull(entry.getValue());
                UUID.fromString(p.id);if(!"system".equals(p.mode))UUID.fromString(p.recipient);
                if(!Set.of("legacy","private","system").contains(p.mode))throw new IllegalStateException("mode");
                if("private".equals(p.mode)){UUID.fromString(p.owner);if(!p.owner.equals(p.recipient))throw new IllegalStateException("owner");}
                if(!entry.getKey().equals(p.name)||p.name.isBlank()||p.dimension==null||p.currency==null||p.currencyName==null||new java.math.BigDecimal(p.amount).signum()<=0 || new java.math.BigDecimal(p.amount).scale()>4
                  ||!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||!Double.isFinite(p.radius)||p.radius<1||p.radius>128||!Double.isFinite(p.height)||p.height<=0)throw new IllegalStateException("point");
                if(!Double.isFinite(p.below())||!Double.isFinite(p.above())||p.below()<0||p.above()<0||p.below()>128||p.above()>128||p.halfHeight()<=0)throw new IllegalStateException("height");
            }
            for(var entry:s.orders.entrySet()) {
                Order o=Objects.requireNonNull(entry.getValue());UUID.fromString(o.player);if(!"system".equals(o.mode))UUID.fromString(o.recipient);UUID.fromString(o.point);
                if(!Set.of("legacy","private","system").contains(o.mode))throw new IllegalStateException("mode");
                if(!Set.of("legacy","toll-v1").contains(o.transport))throw new IllegalStateException("transport");
                if(o.boat!=null)UUID.fromString(o.boat);
                if(!entry.getKey().equals(o.reference)||!o.reference.startsWith("toll:")||o.currency==null||new java.math.BigDecimal(o.amount).signum()<=0 || new java.math.BigDecimal(o.amount).scale()>4||!Set.of("PENDING","PAID","INSUFFICIENT","CONFISCATED","REVIEW").contains(o.status))throw new IllegalStateException("order");
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
        // Persist the renamed directory entry before a newly saved order may reach the bank.
        // Failure after rename is uncertain: caller pauses, never assumes the order was absent.
        try(FileChannel directory=FileChannel.open(path.toAbsolutePath().getParent(),StandardOpenOption.READ)){directory.force(true);}
    }
}
