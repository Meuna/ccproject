
package ccproject.stream;

import java.util.Map;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileNotFoundException;;
import java.util.zip.GZIPInputStream;
import java.time.LocalDateTime;
import org.apache.storm.Config;
import org.apache.storm.topology.IRichSpout;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;


public class AirlineFileSpout implements IRichSpout {

    private SpoutOutputCollector collector;

    private String filepath;
    private Integer nbSpouts;
    private Long throttleMs;
    private transient BufferedReader reader;
    private transient LocalDateTime nextEofTick;

    public AirlineFileSpout() {}

    @Override
    public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
        this.collector = collector;

        this.filepath = (String) conf.get("ccproject.filepath");
        this.nbSpouts = ((Long) conf.get("ccproject.nbspouts")).intValue();
        this.throttleMs = (Long) conf.getOrDefault("ccproject.throttlems", 0L);

        try {
            this.reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(this.filepath))));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Opening spout file failed", e);
        } catch (IOException e) {
            throw new RuntimeException("Something failed with the spout file", e);
        }

        try {
            Integer spoutIndex = context.getThisTaskIndex();
            for (int i = 0; i < spoutIndex; i++) {
                this.reader.readLine();
            }
        } catch (IOException e) {}
    }

    @Override
    public void close() {
        try {
            this.reader.close();
        } catch (IOException e) {}
    }

    @Override
    public void nextTuple() {
        String line = null;
        try {
            line = reader.readLine();
            for (int i = 0; i < this.nbSpouts-1; i++) {
                this.reader.readLine();
            }
        } catch (IOException e) {}
        if (line != null) {
            Object[] fields = line.split(";", 9);
            collector.emit(new Values(fields));
        } else {
            if (this.nextEofTick == null) {
                this.nextEofTick = LocalDateTime.now().plusSeconds(30);
            }
            if (LocalDateTime.now().isAfter(this.nextEofTick)) {
                collector.emit("EOF", new Values("EOF"));
                this.nextEofTick = LocalDateTime.now().plusSeconds(300);
            }
        }
        if (this.throttleMs > 0) {
            try {
                Thread.sleep(throttleMs);
            } catch (InterruptedException e) {}
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(
            "date",
            "dow",
            "origin",
            "dest",
            "carrier",
            "flight",
            "dep_time",
            "dep_delay",
            "arr_delay"
        ));
        declarer.declareStream("EOF", new Fields("EOF"));
    }

    @Override
    public void activate() {
    }

    @Override
    public void deactivate() {
    }

    @Override
    public void ack(Object msgId) {
    }

    @Override
    public void fail(Object msgId) {
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return null;
    }

}
