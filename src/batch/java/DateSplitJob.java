package ccproject.batch.q3;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.lib.IdentityMapper;
import org.apache.hadoop.mapred.KeyValueTextInputFormat;
import org.apache.hadoop.mapred.lib.MultipleTextOutputFormat;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;


public class DateSplitJob extends Configured implements Tool {

    public static class KeySplitOutputFormat extends MultipleTextOutputFormat<Text, Text> {
        @Override
        protected String generateFileNameForKeyValue(Text key, Text value, String leaf) {
            return new Path(key.toString(), leaf).toString();
        }

        @Override
        protected Text generateActualKey(Text key, Text value) {
            return null;
        }
    }

    public int run(final String[] args) throws Exception {
        JobConf conf = new JobConf(super.getConf());

		conf.setJarByClass(DateSplitJob.class);
        conf.setMapperClass(IdentityMapper.class);
        conf.setNumReduceTasks(0);
        conf.setInputFormat(KeyValueTextInputFormat.class);
        conf.setOutputFormat(KeySplitOutputFormat.class);

        FileInputFormat.addInputPath(conf, new Path(args[0]));
        FileOutputFormat.setOutputPath(conf, new Path(args[1]));

        return JobClient.runJob(conf).isSuccessful() ? 0 : 1;
    }

    public static void main(final String[] args) throws Exception {
        int res = ToolRunner.run(new Configuration(), new DateSplitJob(), args);
        System.exit(res);
    }

}

