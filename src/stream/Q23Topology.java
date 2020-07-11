
package ccproject.stream;

import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import org.apache.storm.topology.ConfigurableTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.cassandra.bolt.CassandraWriterBolt;
import static org.apache.storm.cassandra.DynamicStatementBuilder.*;

import ccproject.stream.AirlineFileSpout;

public class Q23Topology extends ConfigurableTopology  {

    public static void main(String[] args) throws Exception {
        ConfigurableTopology.start(new Q23Topology(), args);
    }

    @Override
    protected int run(String[] args) throws Exception {

        Integer nbNodes = 5;
        Integer nbSPouts = nbNodes;

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("airline-ontime-spout", new AirlineFileSpout(), nbSPouts);
        builder.setBolt("odcd-delay-bolt", new OriginDestCarrierDelayBolt(), nbNodes)
            .fieldsGrouping("airline-ontime-spout", new Fields("origin", "dest"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q23entry (origin_dest,carrier,delay) VALUES (?, ?, ?);")
                    .with(
                        fields("origin_dest", "carrier", "delay")
                    )
                )
        ), nbNodes).shuffleGrouping("odcd-delay-bolt");

        conf.put("cassandra.keyspace", "ccproject");
        conf.put("cassandra.nodes", args[1]);
        conf.put("ccproject.filepath", args[0]);
        conf.put("ccproject.nbspouts", nbSPouts);
        conf.put("ccproject.pseudobatch", Boolean.valueOf(args[2]));

        conf.setNumWorkers(nbNodes);

        return submit("q23", conf, builder);
    }

    public static class OriginDestCarrierDelayBolt implements IRichBolt {

        private OutputCollector collector;

        private Boolean pseudoBatch;
        private HashMap<String, Double> odcMeanDelay = new HashMap<>();
        private HashMap<String, Integer> odcFlightCount = new HashMap<>();

        public OriginDestCarrierDelayBolt() {}

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
            this.pseudoBatch = (Boolean) conf.get("ccproject.pseudobatch");
        }

        @Override
        public void execute(Tuple tuple) {
            if (this.pseudoBatch && tuple.getSourceStreamId().equals("EOF")) {
                for (Map.Entry<String, Double> entry : this.odcMeanDelay.entrySet()) {
                    String[] originDestCarrier = entry.getKey().split("_");
                    String originDest = originDestCarrier[0] + '_' + originDestCarrier[1];
                    String carrier = originDestCarrier[2];
                    collector.emit(new Values(originDest, carrier, BigDecimal.valueOf(entry.getValue())));
                }
            } else {
                String origin = tuple.getStringByField("origin");
                String dest = tuple.getStringByField("dest");
                String carrier = tuple.getStringByField("carrier");
                String strDelay = tuple.getStringByField("arr_delay");
                if (!strDelay.equals("")) {
                    String originDestCarrier = origin + '_' + dest + '_' + carrier;
                    Double delay = Double.parseDouble(strDelay);

                    Double currentMean = this.odcMeanDelay.getOrDefault(originDestCarrier, 0.0);
                    Integer currentCount = this.odcFlightCount.getOrDefault(originDestCarrier, 0);

                    Double newMean = (delay + currentMean*currentCount) / (currentCount + 1);

                    this.odcMeanDelay.put(originDestCarrier, newMean);
                    this.odcFlightCount.put(originDestCarrier, currentCount + 1);

                    if (!this.pseudoBatch) {
                        collector.emit(new Values(origin, dest, BigDecimal.valueOf(newMean)));
                    }
                }
            }

            collector.ack(tuple);
        }

        @Override
        public void cleanup() {}

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("origin_dest", "carrier", "delay"));
        }

        @Override
        public Map<String, Object> getComponentConfiguration() {
            return null;
        }
    }
}
