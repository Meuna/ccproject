src_path=$(dirname $0)

out_dir=/ccproject/out/batch/q3
tmp_dir=/ccproject/out/batch/q3_tmp

hdfs dfs -rm -r $out_dir
hdfs dfs -rm -r $tmp_dir

mapred \
    streaming \
    -files $src_path/python \
    -input /ccproject/raw/On_Time_On_Time_Performance_2008_*.gz \
    -output $tmp_dir/step1 \
    -mapper "python/q3.py map-step1" \
    -combiner "python/q3.py reduce-step1" \
    -reducer "python/q3.py reduce-step1"

rm -r tmp_q3
hdfs dfs -text $tmp_dir/step1/part* | python $src_path/python/q3.py split-step1 tmp_q3
hdfs dfs -mkdir -p $tmp_dir/step1_split
hdfs dfs -put tmp_q3/* $tmp_dir/step1_split
rm -r tmp_q3

# Can't make it to work on the cluster
# hadoop \
#     -libjars $src_path/java/q3.jar \
#     ccproject.batch.q3.DateSplitJob \
#     $tmp_dir/step1/part* \
#     $tmp_dir/step1_split

mapred \
    streaming \
    -D mapred.reduce.tasks=0 \
    -libjars $src_path/java/q3.jar \
    -files $src_path/python \
    -inputformat ccproject.batch.q3.SuperSpecificDateCartesianInputFormat \
    -outputformat org.apache.hadoop.mapred.lib.NullOutputFormat \
    -input $tmp_dir/step1_split/*/part* \
    -output $out_dir \
    -mapper "python/q3.py map-step2"
