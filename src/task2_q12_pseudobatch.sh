src_path=$(dirname $0)/stream

LIB_FOLDER=$src_path/lib
CCPROJECT_CLASSPATH=$LIB_FOLDER/cassandra-driver-core-3.1.2.jar,$LIB_FOLDER/storm-cassandra-2.1.0.jar,$LIB_FOLDER/guava-16.0.1.jar,$LIB_FOLDER/commons-lang3-3.3.jar,$LIB_FOLDER/slf4j-api-1.7.6.jar,$LIB_FOLDER/netty-all-4.0.37.Final.jar

storm jar --jars $CCPROJECT_CLASSPATH $src_path/task2.jar \
    ccproject.stream.AllQ12Topology \
    /data/storm/airline_ontime_clean.gz \
    node6,node7,node8,node9,node10 true
