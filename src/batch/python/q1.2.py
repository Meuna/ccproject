#!/usr/bin/env python
import sys
from decimal import Decimal
import mapred

# Compute carrier performance as mean arrival delay.

def mapper(stream):
    fields = ['UniqueCarrier', 'ArrDelay']

    for carrier, delay in mapred.iter_curated_fields(stream, fields):
        mapred.send(carrier, (delay, 1))

def reducer(stream):
    carrier_mean_delay = mapred.mean_accumulator_reducer(stream)

    for carrier, mean_count in carrier_mean_delay.iteritems():
        mapred.send(carrier, mean_count)

def load_db(stream):
    import boto3
    dynamodb = boto3.resource('dynamodb', region_name='us-east-1')
    table = dynamodb.Table('ccproject_q1.2')
    with table.batch_writer() as batch:
        for carrier, (delay, _) in mapred.iter_key_values(stream):
            batch.put_item(Item={
                'dummy_key': 1,
                'carrier': carrier,
                'delay': Decimal(delay),
            })


if __name__ == '__main__':
    if sys.argv[1] == 'map':
        mapper(sys.stdin)
    elif sys.argv[1] == 'reduce':
        reducer(sys.stdin)
    elif sys.argv[1] == 'load-db':
        load_db(sys.stdin)
