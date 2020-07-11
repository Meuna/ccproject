package ccproject.batch.q3;

import java.util.ArrayList;
import java.util.HashMap;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.IOException;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.join.CompositeInputSplit;

// Adapted from https://github.com/adamjshook/mapreducepatterns/blob/master/MRDP/src/main/java/mrdp/ch5/CartesianProduct.java

@SuppressWarnings({ "unchecked" })
public class SuperSpecificDateCartesianInputFormat extends FileInputFormat {

    @Override
    public InputSplit[] getSplits(JobConf conf, int numSplits)  throws IOException {

        // Get the default input splits
        TextInputFormat dummyFIF = new TextInputFormat();
        dummyFIF.configure(conf);
        FileSplit[] baseSplits = (FileSplit[]) dummyFIF.getSplits(conf, numSplits);

        // Bucket our afternoon splits by day
        HashMap<LocalDate, ArrayList<FileSplit>> splitsByDay = new HashMap<LocalDate, ArrayList<FileSplit>>();
        for (FileSplit split: baseSplits) {
            String splitDateAndTimeStr = split.getPath().getParent().getName();
            String[] splitParts = splitDateAndTimeStr.split("__");
            String dateStr = splitParts[0];
            String timeStr = splitParts[1];

            if (timeStr.equals("PM")) {
                LocalDate splitDate = LocalDate.parse(dateStr);
                splitsByDay.putIfAbsent(splitDate, new ArrayList<FileSplit>());
                splitsByDay.get(splitDate).add(split);
            }
        }

        // Create our CompositeInputSplits as an ArrayList because size is a bit annoying to compute
        ArrayList<CompositeInputSplit> returnSplits = new ArrayList<CompositeInputSplit>();

        int nbSplits = 0;
        // For each morning input splits
        for (FileSplit firstLegSplit : baseSplits) {
            String splitDateAndTimeStr = firstLegSplit.getPath().getParent().getName();
            String[] splitParts = splitDateAndTimeStr.split("__");
            String dateStr = splitParts[0];
            String timeStr = splitParts[1];

            LocalDate secondLegDate = LocalDate.parse(dateStr).plusDays(2);
            ArrayList<FileSplit> secondLegSplitsBucket = splitsByDay.get(secondLegDate);

            // This split will be matched to all of the splits in the secondLegDate bucket is any
            if (timeStr.equals("AM") && (secondLegSplitsBucket != null)) {
                for (FileSplit secondLegSplit : secondLegSplitsBucket) {
                    // Create a new composite input split composing of the two
                    CompositeInputSplit fullTripSplit = new CompositeInputSplit(2);
                    fullTripSplit.add(firstLegSplit);
                    fullTripSplit.add(secondLegSplit);
                    returnSplits.add(fullTripSplit);
                    ++nbSplits;
                }
            }
        }

        // Return the composite splits
        LOG.info("Total splits to process: " + nbSplits);
        return returnSplits.toArray(new CompositeInputSplit[returnSplits.size()]);
    }

    @Override
    public RecordReader getRecordReader(InputSplit split, JobConf conf,
            Reporter reporter) throws IOException {
        // create a new instance of the Cartesian record reader
        return new CartesianRecordReader((CompositeInputSplit) split, conf, reporter);
    }

    public static class CartesianRecordReader<K1, V1, K2, V2> implements
            RecordReader<Text, Text> {

        // Record readers to get key value pairs
        private RecordReader leftRR = null, rightRR = null;

        // Store configuration to re-create the right record reader
        private FileInputFormat rightFIF;
        private JobConf rightConf;
        private InputSplit rightIS;
        private Reporter rightReporter;

        // Helper variables
        private K1 lkey;
        private V1 lvalue;
        private K2 rkey;
        private V2 rvalue;
        private boolean goToNextLeft = true, alldone = false;

        public CartesianRecordReader(CompositeInputSplit split, JobConf conf,
                Reporter reporter) throws IOException {
            this.rightConf = conf;
            this.rightIS = split.get(1);
            this.rightReporter = reporter;

            // Create left & right record reader
            leftRR = new TextInputFormat().getRecordReader(split.get(0), conf, reporter);
            rightFIF = new TextInputFormat();
            rightRR = rightFIF.getRecordReader(rightIS, rightConf, rightReporter);

            // Create key value pairs for parsing
            lkey = (K1) this.leftRR.createKey();
            lvalue = (V1) this.leftRR.createValue();

            rkey = (K2) this.rightRR.createKey();
            rvalue = (V2) this.rightRR.createValue();
        }

        @Override
        public Text createKey() {
            return new Text();
        }

        @Override
        public Text createValue() {
            return new Text();
        }

        @Override
        public long getPos() throws IOException {
            return leftRR.getPos();
        }

        @Override
        public boolean next(Text key, Text value) throws IOException {

            do {
                // If we are to go to the next left key/value pair
                if (goToNextLeft) {
                    // Read the next key value pair, false means no more pairs
                    if (!leftRR.next(lkey, lvalue)) {
                        // If no more, then this task is nearly finished
                        alldone = true;
                        break;
                    } else {
                        // If we aren't done, set the value to the key and set
                        // our flags
                        key.set(lvalue.toString());
                        goToNextLeft = alldone = false;

                        // Reset the right record reader
                        rightRR = rightFIF.getRecordReader(
                            rightIS, rightConf, rightReporter);
                    }
                }

                // Read the next key value pair from the right data set
                if (rightRR.next(rkey, rvalue)) {
                    // If success, set the value
                    value.set(rvalue.toString());
                } else {
                    // Otherwise, this right data set is complete
                    // and we should go to the next left pair
                    goToNextLeft = true;
                }

                // This loop will continue if we finished reading key/value
                // pairs from the right data set
            } while (goToNextLeft);

            // Return true if a key/value pair was read, false otherwise
            return !alldone;
        }

        @Override
        public void close() throws IOException {
            leftRR.close();
            rightRR.close();
        }

        @Override
        public float getProgress() throws IOException {
            return leftRR.getProgress();
        }
    }

}
