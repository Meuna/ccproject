
package ccproject.stream;

import java.util.Map;
import java.util.HashMap;
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

public class Q11Topology extends ConfigurableTopology  {

    public static void main(String[] args) throws Exception {
        ConfigurableTopology.start(new Q11Topology(), args);
    }

    @Override
    protected int run(String[] args) throws Exception {

        Integer nbNodes = 5;
        Integer nbSPouts = nbNodes;

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("airline-ontime-spout", new AirlineFileSpout(), nbSPouts);
        builder.setBolt("airport-flat-bolt", new AirportFlatBolt(), nbNodes).shuffleGrouping("airline-ontime-spout");
        builder.setBolt("airport-count-bolt", new AirportCountBolt(), nbNodes)
            .fieldsGrouping("airport-flat-bolt", new Fields("airport"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q11entry (airport,count) VALUES (?, ?);")
                    .with(
                        fields("airport", "count")
                    )
                )
        ), nbNodes).shuffleGrouping("airport-count-bolt");

        conf.put("cassandra.keyspace", "ccproject");
        conf.put("cassandra.nodes", args[1]);
        conf.put("ccproject.filepath", args[0]);
        conf.put("ccproject.nbspouts", nbSPouts);
        conf.put("ccproject.pseudobatch", Boolean.valueOf(args[2]));

        conf.setNumWorkers(nbNodes);

        return submit("q11", conf, builder);
    }

    public static class AirportFlatBolt implements IRichBolt {

        private OutputCollector collector;

        public AirportFlatBolt() {}

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
        }

        @Override
        public void execute(Tuple tuple) {
            String origin = tuple.getStringByField("origin");
            String dest = tuple.getStringByField("dest");


            collector.emit(new Values(origin));
            collector.emit(new Values(dest));

            collector.ack(tuple);
        }

        @Override
        public void cleanup() {}

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("airport"));
        }

        @Override
        public Map<String, Object> getComponentConfiguration() {
            return null;
        }
    }

    public static class AirportCountBolt implements IRichBolt {

        private OutputCollector collector;

        private Boolean pseudoBatch;
        private HashMap<String, Integer> airportCount = new HashMap<>();

        public AirportCountBolt() {}

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
            this.pseudoBatch = (Boolean) conf.get("ccproject.pseudobatch");
        }

        @Override
        public void execute(Tuple tuple) {
            if (this.pseudoBatch && tuple.getSourceStreamId().equals("EOF")) {
                for (Map.Entry<String, Integer> entry : this.airportCount.entrySet()) {
                    collector.emit(new Values(entry.getKey(), entry.getValue()));
                }
            } else {
                String airport = tuple.getStringByField("airport");
                this.airportCount.put(airport, this.airportCount.getOrDefault(airport, 0) + 1);
                if (!this.pseudoBatch) {
                    collector.emit(new Values(airport, this.airportCount.get(airport)));
                }
            }
            collector.ack(tuple);
        }

        @Override
        public void cleanup() {}

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("airport", "count"));
        }

        @Override
        public Map<String, Object> getComponentConfiguration() {
            return null;
        }
    }
}
