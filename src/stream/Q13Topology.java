
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

public class Q13Topology extends ConfigurableTopology  {

    public static void main(String[] args) throws Exception {
        ConfigurableTopology.start(new Q13Topology(), args);
    }

    @Override
    protected int run(String[] args) throws Exception {

        Integer nbNodes = 5;
        Integer nbSPouts = nbNodes;

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("airline-ontime-spout", new AirlineFileSpout(), nbSPouts);
        builder.setBolt("dow-delay-bolt", new DowDelayBolt(), nbNodes)
            .fieldsGrouping("airline-ontime-spout", new Fields("dow"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q13entry (dow,delay) VALUES (?, ?);")
                    .with(
                        fields("dow", "delay")
                    )
                )
        ), nbNodes).shuffleGrouping("dow-delay-bolt");

        conf.put("cassandra.keyspace", "ccproject");
        conf.put("cassandra.nodes", args[1]);
        conf.put("ccproject.filepath", args[0]);
        conf.put("ccproject.nbspouts", nbSPouts);
        conf.put("ccproject.pseudobatch", Boolean.valueOf(args[2]));

        conf.setNumWorkers(nbNodes);

        return submit("q13", conf, builder);
    }

    public static class DowDelayBolt implements IRichBolt {

        public final String[] dowMap = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

        private OutputCollector collector;

        private Boolean pseudoBatch;
        private HashMap<String, Double> dowMeanDelay = new HashMap<>();
        private HashMap<String, Integer> dowFlightCount = new HashMap<>();

        public DowDelayBolt() {}

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
            this.pseudoBatch = (Boolean) conf.get("ccproject.pseudobatch");
        }

        @Override
        public void execute(Tuple tuple) {
            if (this.pseudoBatch && tuple.getSourceStreamId().equals("EOF")) {
                for (Map.Entry<String, Double> entry : this.dowMeanDelay.entrySet()) {
                    collector.emit(new Values(entry.getKey(), BigDecimal.valueOf(entry.getValue())));
                }
            } else {
                String strDelay = tuple.getStringByField("arr_delay");
                if (!strDelay.equals("")) {
                    Integer dowId = Integer.parseInt(tuple.getStringByField("dow"));
                    String dow = this.dowMap[dowId-1];
                    Double delay = Double.parseDouble(strDelay);

                    Double currentMean = this.dowMeanDelay.getOrDefault(dow, 0.0);
                    Integer currentCount = this.dowFlightCount.getOrDefault(dow, 0);

                    Double newMean = (delay + currentMean*currentCount) / (currentCount + 1);

                    this.dowMeanDelay.put(dow, newMean);
                    this.dowFlightCount.put(dow, currentCount + 1);

                    if (!this.pseudoBatch) {
                        collector.emit(new Values(dow, BigDecimal.valueOf(newMean)));
                    }
                }
            }

            collector.ack(tuple);
        }

        @Override
        public void cleanup() {}

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("dow", "delay"));
        }

        @Override
        public Map<String, Object> getComponentConfiguration() {
            return null;
        }
    }
}
