
package ccproject.stream;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.storm.topology.ConfigurableTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseWindowedBolt.Duration;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.bolt.JoinBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.cassandra.bolt.CassandraWriterBolt;
import static org.apache.storm.cassandra.DynamicStatementBuilder.*;

import ccproject.stream.AirlineFileSpout;

public class Q3Topology extends ConfigurableTopology  {

    public static void main(String[] args) throws Exception {
        ConfigurableTopology.start(new Q3Topology(), args);
    }

    @Override
    protected int run(String[] args) throws Exception {

        Integer nbNodes = 5;
        Integer nbSPouts = 1;

        JoinBolt jbolt =  new JoinBolt("morning-best-bolt", "stopover")
            .join("afternoon-best-bolt", "stopover",  "morning-best-bolt")
            .select("morning-best-bolt:date, afternoon-best-bolt:date, morning-best-bolt:origin, morning-best-bolt:dest, afternoon-best-bolt:dest, morning-best-bolt:arr_delay, "
                    + "afternoon-best-bolt:arr_delay, morning-best-bolt:dep_time, afternoon-best-bolt:dep_time, morning-best-bolt:flight, afternoon-best-bolt:flight")
            .withTumblingWindow(new Duration(5, TimeUnit.MINUTES)) ;

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("airline-ontime-spout", new AirlineFileSpout(), nbSPouts);
        builder.setBolt("noon-flight-split-bolt", new NoonFlightSplitBolt(), nbSPouts).shuffleGrouping("airline-ontime-spout");
        builder.setBolt("morning-best-bolt", new BestFlightBolt(), nbNodes)
            .fieldsGrouping("noon-flight-split-bolt", "MORNING", new Fields("origin", "dest"));
        builder.setBolt("afternoon-best-bolt", new BestFlightBolt(), nbNodes)
            .fieldsGrouping("noon-flight-split-bolt", "AFTERNOON", new Fields("origin", "dest"));
        builder.setBolt("leg-join", jbolt, nbNodes)
            .fieldsGrouping("morning-best-bolt", new Fields("stopover"))
            .fieldsGrouping("afternoon-best-bolt", new Fields("stopover"));
        builder.setBolt("trip-builder-bolt", new TripBuilderBolt(), nbNodes).shuffleGrouping("leg-join");
        builder.setBolt("db-bolt", new CassandraWriterBolt(
            async(
                simpleQuery("INSERT INTO q3entry (date_origin_dest1,date1,origin,dest1,dest2,total_delay,datetime1,flight1,datetime2,flight2) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);")
                    .with(
                        fields("date_origin_dest1", "date1", "origin", "dest1", "dest2", "total_delay", "datetime1", "flight1", "datetime2", "flight2")
                    )
                )
        ), nbNodes).shuffleGrouping("trip-builder-bolt");

        conf.put("cassandra.keyspace", "ccproject");
        conf.put("cassandra.nodes", args[1]);
        conf.put("ccproject.filepath", args[0]);
        conf.put("ccproject.nbspouts", nbSPouts);
        conf.put("ccproject.throttlems", 1);
        conf.put("topology.message.timeout.secs", 360000);
        conf.put(conf.TOPOLOGY_WORKER_MAX_HEAP_SIZE_MB, 4*1024);
        conf.setNumWorkers(nbNodes);

        return submit("q3", conf, builder);
    }


    public static class NoonFlightSplitBolt implements IRichBolt {

        private OutputCollector collector;

        public NoonFlightSplitBolt() {}

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
        }

        @Override
        public void execute(Tuple tuple) {
            Boolean tupleIsComplete = true;
            for (int i = 0; i < tuple.size(); i++) {
                if (tuple.getString(i).equals("")) {
                    tupleIsComplete = false;
                    break;
                }
            }

            if (tupleIsComplete) {
                String depTimeStr = tuple.getStringByField("dep_time");
                Integer depHour = Integer.parseInt(depTimeStr.substring(0,2));

                // outTuple[0:8] contain the input tuple.
                // outTuple[9] will contain the join key, set as origin_date of the second leg flight.
                Object[] outTuple = new String[10];
                for (int i = 0; i < 9; i++) {
                    outTuple[i] = tuple.getString(i);
                }

                if (depHour < 12) {
                    // For morning flights, we must compute the second leg flight from the destination and date + 2days.
                    String dateStr = tuple.getStringByField("date");
                    String secondLegDateStr = LocalDate.parse(dateStr).plusDays(2).toString();
                    outTuple[9] = tuple.getStringByField("dest") + "_" + secondLegDateStr;

                    collector.emit("MORNING", new Values(outTuple));
                } else {
                    // For afternoon flights, we simply set origin_date as they are the second leg.
                    outTuple[9] = tuple.getStringByField("origin") + "_" + tuple.getStringByField("date");

                    collector.emit("AFTERNOON", new Values(outTuple));
                }
            }

            collector.ack(tuple);
        }

        @Override
        public void cleanup() {}

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declareStream("MORNING", new Fields(
                "date",
                "dow",
                "origin",
                "dest",
                "carrier",
                "flight",
                "dep_time",
                "dep_delay",
                "arr_delay",
                "stopover"
            ));
            declarer.declareStream("AFTERNOON", new Fields(
                "date",
                "dow",
                "origin",
                "dest",
                "carrier",
                "flight",
                "dep_time",
                "dep_delay",
                "arr_delay",
                "stopover"
            ));
        }

        @Override
        public Map<String, Object> getComponentConfiguration() {
            return null;
        }
    }

    public static class BestFlightBolt implements IRichBolt {

        private OutputCollector collector;

        private HashMap<String, Object[]> bestLegs = new HashMap<>();
        private HashMap<String, String> legDates = new HashMap<>();
        private HashMap<String, Double> legDelays = new HashMap<>();

        public BestFlightBolt() {}

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
        }

        @Override
        public void execute(Tuple tuple) {
            String leg = tuple.getStringByField("origin") + '_' + tuple.getStringByField("dest");
            String date = tuple.getStringByField("date");
            Double delay = Double.parseDouble(tuple.getStringByField("arr_delay"));

            String currentDate = this.legDates.getOrDefault(leg, date);
            Double currentBestDelay = this.legDelays.getOrDefault(leg, Double.POSITIVE_INFINITY);

            if (!date.equals(currentDate)) {
                collector.emit(new Values(this.bestLegs.get(leg)));
                this.bestLegs.put(leg, tuple.getValues().toArray());
                this.legDates.put(leg, date);
                this.legDelays.put(leg, delay);
            } else if (delay < currentBestDelay) {
                this.bestLegs.put(leg, tuple.getValues().toArray());
                this.legDates.put(leg, date);
                this.legDelays.put(leg, delay);
            }

            collector.ack(tuple);
        }

        @Override
        public void cleanup() {}

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
                "arr_delay",
                "stopover"
            ));
        }

        @Override
        public Map<String, Object> getComponentConfiguration() {
            return null;
        }
    }

    public static class TripBuilderBolt implements IRichBolt {

        private OutputCollector collector;

        public TripBuilderBolt() {}

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
        }

        @Override
        public void execute(Tuple tuple) {
            String date1 = tuple.getStringByField("morning-best-bolt:date");
            String date2 = tuple.getStringByField("afternoon-best-bolt:date");
            String origin = tuple.getStringByField("morning-best-bolt:origin");
            String dest1 = tuple.getStringByField("morning-best-bolt:dest");
            String dest2 = tuple.getStringByField("afternoon-best-bolt:dest");
            String delay1 = tuple.getStringByField("morning-best-bolt:arr_delay");
            String delay2 = tuple.getStringByField("afternoon-best-bolt:arr_delay");
            String depTime1 = tuple.getStringByField("morning-best-bolt:dep_time");
            String depTime2 = tuple.getStringByField("afternoon-best-bolt:dep_time");
            String flight1 = tuple.getStringByField("morning-best-bolt:flight");
            String flight2 = tuple.getStringByField("afternoon-best-bolt:flight");

            String dateOriginDest1 = date1 + '_' + origin + '_' + dest1;
            Double totalDelay = Double.parseDouble(delay1) + Double.parseDouble(delay2);
            String datetime1 = date1 + ' ' + depTime1;
            String datetime2 = date2 + ' ' + depTime2;
            collector.emit(new Values(dateOriginDest1, date1, origin, dest1, dest2, BigDecimal.valueOf(totalDelay), datetime1, flight1, datetime2, flight2));
            collector.ack(tuple);
        }

        @Override
        public void cleanup() {}

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields(
                "date_origin_dest1",
                "date1",
                "origin",
                "dest1",
                "dest2",
                "total_delay",
                "datetime1",
                "flight1",
                "datetime2",
                "flight2"
            ));
        }

        @Override
        public Map<String, Object> getComponentConfiguration() {
            return null;
        }
    }

}
