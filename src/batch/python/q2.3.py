#!/usr/bin/env python
import sys
from decimal import Decimal
import mapred

# For each origin/destination, compute carrier perfomance as mean arrival delay.

def mapper(stream):
    fields = ['Origin', 'Dest', 'UniqueCarrier', 'ArrDelay']

    for origin, dest, carrier, delay in mapred.iter_curated_fields(stream, fields):
        mapred.send((origin, dest, carrier), (delay, 1))

def reducer(stream):
    depdest_carr_mean_delay = mapred.mean_accumulator_reducer(stream)

    for (origin, dest, carrier), mean_count in depdest_carr_mean_delay.iteritems():
        mapred.send((origin, dest, carrier), mean_count)

def load_db(stream):
    from cassandra.cluster import Cluster
    from cassandra.cqlengine import connection
    from decimal import Decimal

    from cassandra.cqlengine import columns
    from cassandra.cqlengine.models import Model

    from setup_cassandradb import Q23Entry

    cluster = Cluster(['node7', 'node8', 'node9'])
    connection.register_connection('con', session=cluster.connect(), default=True)

    for (origin, dest, carrier), (delay, _) in mapred.iter_key_values(stream):
        Q23Entry.create(
            origin_dest = origin + '_' + dest,
            carrier = carrier,
            delay = Decimal(delay)
        )


if __name__ == '__main__':
    if sys.argv[1] == 'map':
        mapper(sys.stdin)
    elif sys.argv[1] == 'reduce':
        reducer(sys.stdin)
    elif sys.argv[1] == 'load-db':
        load_db(sys.stdin)