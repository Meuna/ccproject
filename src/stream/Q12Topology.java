
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

public class Q12Topology extends ConfigurableTopology  {

    public static void main(String[] args) throws Exception {
        ConfigurableTopology.start(new Q12Topology(), args);
    }

    @Override
    protected int run(String[] args) throws Exception {

        Integer nbNodes = 5;
        Integer nbSPouts = nbNodes;

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("airline-ontime-spout", new AirlineFileSpout(), nbSPouts);
        builder.setBolt("carrier-delay-bolt", new CarrierDelayBolt(), nbNodes)
            .fieldsGrouping("airline-ontime-spout", new Fields("carrier"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q12entry (carrier,delay) VALUES (?, ?);")
                    .with(
                        fields("carrier", "delay")
                    )
                )
        ), nbNodes).shuffleGrouping("carrier-delay-bolt");

        conf.put("cassandra.keyspace", "ccproject");
        conf.put("cassandra.nodes", args[1]);
        conf.put("ccproject.filepath", args[0]);
        conf.put("ccproject.nbspouts", nbSPouts);
        conf.put("ccproject.pseudobatch", Boolean.valueOf(args[2]));

        conf.setNumWorkers(nbNodes);

        return submit("q12", conf, builder);
    }

    public static class CarrierDelayBolt implements IRichBolt {

        private OutputCollector collector;

        private Boolean pseudoBatch;
        private HashMap<String, Double> carrierMeanDelay = new HashMap<>();
        private HashMap<String, Integer> carrierFlightCount = new HashMap<>();

        public CarrierDelayBolt() {}

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
            this.pseudoBatch = (Boolean) conf.get("ccproject.pseudobatch");
        }

        @Override
        public void execute(Tuple tuple) {
            if (this.pseudoBatch && tuple.getSourceStreamId().equals("EOF")) {
                for (Map.Entry<String, Double> entry : this.carrierMeanDelay.entrySet()) {
                    collector.emit(new Values(entry.getKey(), BigDecimal.valueOf(entry.getValue())));
                }
            } else {
                String carrier = tuple.getStringByField("carrier");
                String strDelay = tuple.getStringByField("arr_delay");
                if (!strDelay.equals("")) {
                    Double delay = Double.parseDouble(strDelay);

                    Double currentMean = this.carrierMeanDelay.getOrDefault(carrier, 0.0);
                    Integer currentCount = this.carrierFlightCount.getOrDefault(carrier, 0);

                    Double newMean = (delay + currentMean*currentCount) / (currentCount + 1);

                    this.carrierMeanDelay.put(carrier, newMean);
                    this.carrierFlightCount.put(carrier, currentCount + 1);

                    if (!this.pseudoBatch) {
                        collector.emit(new Values(carrier, BigDecimal.valueOf(newMean)));
                    }
                }
            }

            collector.ack(tuple);
        }

        @Override
        public void cleanup() {}

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("carrier", "delay"));
        }

        @Override
        public Map<String, Object> getComponentConfiguration() {
            return null;
        }
    }
}
