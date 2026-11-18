package dev.jdguo.toll;

import java.io.IOException;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** The bank checkpoint is authoritative. Legacy JSON is migration input, never a fallback. */
public final class TollStateRepository {
    public final TollStore state;
    private long revision;
    private boolean failed;
    private TollStateRepository(TollStore state,long revision){this.state=state;this.revision=revision;}
    public static TollStateRepository open(Path legacyFile)throws IOException {
        if(!BankBridge.stateApi())throw new IOException("Bank lacks authoritative toll state API; upgrade bank, no local fallback");
        try {
            var stored=BankBridge.loadState();
            var state=stored.payload()==null?TollStore.load(legacyFile):TollStore.fromJson(stored.payload());
            var repository=new TollStateRepository(state,stored.revision());
            if(stored.payload()==null)repository.save();
            repository.guardLegacyFile(legacyFile);
            return repository;
        }catch(ReflectiveOperationException|RuntimeException e){throw new IOException("Bank toll state unavailable; no local fallback",e);}
    }
    /** Compatibility guard only, never financial authority. Old schema-1 jars refuse this file.
     * This is defense in depth, not a promise against hardware rollback/deleting the marker. */
    private void guardLegacyFile(Path file)throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path backup=file.resolveSibling(file.getFileName()+".pre-bank-migration");
        if(Files.exists(file)&&!Files.exists(backup)){
            Files.copy(file,backup);
            try(var channel=FileChannel.open(backup,StandardOpenOption.WRITE)){channel.force(true);}
        }
        var json=com.google.gson.JsonParser.parseString(state.toJson()).getAsJsonObject();
        json.addProperty("schema",2);
        json.addProperty("migration","Bank checkpoint is authoritative. Do not run older toll or bank jars on this world.");
        Path temporary=file.resolveSibling(file.getFileName()+".guard.tmp");
        byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);
        try(var channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
            var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }
        Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    public void save()throws IOException {
        if(failed)throw new IOException("State session is paused after an uncertain/failed save; reload bank checkpoint");
        var result=BankBridge.saveState(revision,state.toJson());
        if(!result.success()){failed=true;throw new IOException("Bank toll state persistence: "+result.code());}
        revision++;
    }
}
