
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

public class Q21Topology extends ConfigurableTopology  {

    public static void main(String[] args) throws Exception {
        ConfigurableTopology.start(new Q21Topology(), args);
    }

    @Override
    protected int run(String[] args) throws Exception {

        Integer nbNodes = 5;
        Integer nbSPouts = nbNodes;

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("airline-ontime-spout", new AirlineFileSpout(), nbSPouts);
        builder.setBolt("ocd-delay-bolt", new OriginCarrierDelayBolt(), nbNodes)
            .fieldsGrouping("airline-ontime-spout", new Fields("origin", "carrier"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q21entry (origin,carrier,delay) VALUES (?, ?, ?);")
                    .with(
                        fields("origin", "carrier", "delay")
                    )
                )
        ), nbNodes).shuffleGrouping("ocd-delay-bolt");

        conf.put("cassandra.keyspace", "ccproject");
        conf.put("cassandra.nodes", args[1]);
        conf.put("ccproject.filepath", args[0]);
        conf.put("ccproject.nbspouts", nbSPouts);
        conf.put("ccproject.pseudobatch", Boolean.valueOf(args[2]));

        conf.setNumWorkers(nbNodes);

        return submit("q21", conf, builder);
    }

    public static class OriginCarrierDelayBolt implements IRichBolt {

        private OutputCollector collector;

        private Boolean pseudoBatch;
        private HashMap<String, Double> ocMeanDelay = new HashMap<>();
        private HashMap<String, Integer> ocFlightCount = new HashMap<>();

        public OriginCarrierDelayBolt() {}

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
            this.pseudoBatch = (Boolean) conf.get("ccproject.pseudobatch");
        }

        @Override
        public void execute(Tuple tuple) {
            if (this.pseudoBatch && tuple.getSourceStreamId().equals("EOF")) {
                for (Map.Entry<String, Double> entry : this.ocMeanDelay.entrySet()) {
                    String[] originCarrier = entry.getKey().split("_");
                    collector.emit(new Values(originCarrier[0], originCarrier[1], BigDecimal.valueOf(entry.getValue())));
                }
            } else {
                String origin = tuple.getStringByField("origin");
                String carrier = tuple.getStringByField("carrier");
                String strDelay = tuple.getStringByField("dep_delay");
                if (!strDelay.equals("")) {
                    String originCarrier = origin + '_' + carrier;
                    Double delay = Double.parseDouble(strDelay);

                    Double currentMean = this.ocMeanDelay.getOrDefault(originCarrier, 0.0);
                    Integer currentCount = this.ocFlightCount.getOrDefault(originCarrier, 0);

                    Double newMean = (delay + currentMean*currentCount) / (currentCount + 1);

                    this.ocMeanDelay.put(originCarrier, newMean);
                    this.ocFlightCount.put(originCarrier, currentCount + 1);

                    if (!this.pseudoBatch) {
                        collector.emit(new Values(origin, carrier, BigDecimal.valueOf(newMean)));
                    }
                }
            }

            collector.ack(tuple);
        }

        @Override
        public void cleanup() {}

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("origin", "carrier", "delay"));
        }

        @Override
        public Map<String, Object> getComponentConfiguration() {
            return null;
        }
    }
}
