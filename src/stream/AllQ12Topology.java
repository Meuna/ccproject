
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
import ccproject.stream.Q11Topology;
import ccproject.stream.Q12Topology;
import ccproject.stream.Q13Topology;
import ccproject.stream.Q21Topology;
import ccproject.stream.Q22Topology;
import ccproject.stream.Q23Topology;
import ccproject.stream.Q24Topology;

public class AllQ12Topology extends ConfigurableTopology  {


    public static void main(String[] args) throws Exception {
        ConfigurableTopology.start(new AllQ12Topology(), args);
    }

    @Override
    protected int run(String[] args) throws Exception {

        Integer nbNodes = 5;
        Integer nbSPouts = nbNodes;

        TopologyBuilder builder = new TopologyBuilder();

        // Airlone on time spout
        builder.setSpout("airline-ontime-spout", new AirlineFileSpout(), nbSPouts);

        // Q1.1 topology
        builder.setBolt("airport-flat-bolt-q11", new Q11Topology.AirportFlatBolt(), nbNodes).shuffleGrouping("airline-ontime-spout");
        builder.setBolt("airport-count-bolt-q11", new Q11Topology.AirportCountBolt(), nbNodes)
            .fieldsGrouping("airport-flat-bolt-q11", new Fields("airport"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt-q11", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q11entry (airport,count) VALUES (?, ?);")
                    .with(
                        fields("airport", "count")
                    )
                )
        ), nbNodes).shuffleGrouping("airport-count-bolt-q11");

        // Q1.2 topology
        builder.setBolt("carrier-delay-bolt-q12", new Q12Topology.CarrierDelayBolt(), nbNodes)
            .fieldsGrouping("airline-ontime-spout", new Fields("carrier"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt-q12", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q12entry (carrier,delay) VALUES (?, ?);")
                    .with(
                        fields("carrier", "delay")
                    )
                )
        ), nbNodes).shuffleGrouping("carrier-delay-bolt-q12");

        // Q1.3 topology
        builder.setBolt("dow-delay-bolt-q13", new Q13Topology.DowDelayBolt(), nbNodes)
            .fieldsGrouping("airline-ontime-spout", new Fields("dow"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt-q13", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q13entry (dow,delay) VALUES (?, ?);")
                    .with(
                        fields("dow", "delay")
                    )
                )
        ), nbNodes).shuffleGrouping("dow-delay-bolt-q13");

        // Q2.1 topology
        builder.setBolt("ocd-delay-bolt-q21", new Q21Topology.OriginCarrierDelayBolt(), nbNodes)
            .fieldsGrouping("airline-ontime-spout", new Fields("origin", "carrier"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt-q21", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q21entry (origin,carrier,delay) VALUES (?, ?, ?);")
                    .with(
                        fields("origin", "carrier", "delay")
                    )
                )
        ), nbNodes).shuffleGrouping("ocd-delay-bolt-q21");

        // Q2.2 topology
        builder.setBolt("odd1-delay-bolt-q22", new Q22Topology.OriginDestDelayBolt(), nbNodes)
            .fieldsGrouping("airline-ontime-spout", new Fields("origin", "dest"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt-q22", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q22entry (origin,dest,delay) VALUES (?, ?, ?);")
                    .with(
                        fields("origin", "dest", "delay")
                    )
                )
        ), nbNodes).shuffleGrouping("odd1-delay-bolt-q22");

        // Q2.3 topology
        builder.setBolt("odcd-delay-bolt-q23", new Q23Topology.OriginDestCarrierDelayBolt(), nbNodes)
            .fieldsGrouping("airline-ontime-spout", new Fields("origin", "dest"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt-q23", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q23entry (origin_dest,carrier,delay) VALUES (?, ?, ?);")
                    .with(
                        fields("origin_dest", "carrier", "delay")
                    )
                )
        ), nbNodes).shuffleGrouping("odcd-delay-bolt-q23");

        // Q2.4 topology
        builder.setBolt("odd-delay-bolt-q24", new Q24Topology.OriginDestDelayBolt(), nbNodes)
            .fieldsGrouping("airline-ontime-spout", new Fields("origin", "dest"))
            .allGrouping("airline-ontime-spout", "EOF");
        builder.setBolt("db-bolt-q24", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q24entry (origin_dest,delay) VALUES (?, ?);")
                    .with(
                        fields("origin_dest", "delay")
                    )
                )
        ), nbNodes).shuffleGrouping("odd-delay-bolt-q24");

        conf.put("cassandra.keyspace", "ccproject");
        conf.put("cassandra.nodes", args[1]);
        conf.put("ccproject.filepath", args[0]);
        conf.put("ccproject.nbspouts", nbSPouts);
        conf.put("ccproject.pseudobatch", Boolean.valueOf(args[2]));

        conf.setNumWorkers(nbNodes);

        return submit("all-q12", conf, builder);
    }
}
