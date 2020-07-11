src_path=$(dirname $0)

out_dir=/ccproject/out/batch/$1

hdfs dfs -rm -r $out_dir

mapred \
    streaming \
    -files $src_path/python \
    -input /ccproject/raw/* \
    -output $out_dir \
    -mapper "python/$1.py map" \
    -combiner "python/$1.py reduce" \
    -reducer "python/$1.py reduce"

hdfs dfs -text $out_dir/part* | python $src_path/python/$1.py load-db
