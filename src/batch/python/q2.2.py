#!/usr/bin/env python
import sys
from decimal import Decimal
import mapred

# For each origin, compute destination perfomance as mean departure delay.

def mapper(stream):
    fields = ['Origin', 'Dest', 'DepDelay']

    for origin, dest, delay in mapred.iter_curated_fields(stream, fields):
        mapred.send((origin, dest), (delay, 1))

def reducer(stream):
    dep_dest_mean_delay = mapred.mean_accumulator_reducer(stream)

    for (origin, dest), mean_count in dep_dest_mean_delay.iteritems():
        mapred.send((origin, dest), mean_count)

def load_db(stream):
    from cassandra.cluster import Cluster
    from cassandra.cqlengine import connection
    from decimal import Decimal

    from cassandra.cqlengine import columns
    from cassandra.cqlengine.models import Model

    from setup_cassandradb import Q22Entry

    cluster = Cluster(['node7', 'node8', 'node9'])
    connection.register_connection('con', session=cluster.connect(), default=True)

    for (origin, dest), (delay, _) in mapred.iter_key_values(stream):
        Q22Entry.create(
            origin =  origin,
            dest =  dest,
            delay =  Decimal(delay)
        )


if __name__ == '__main__':
    if sys.argv[1] == 'map':
        mapper(sys.stdin)
    elif sys.argv[1] == 'reduce':
        reducer(sys.stdin)
    elif sys.argv[1] == 'load-db':
        load_db(sys.stdin)
