#!/usr/bin/env python
import sys
from decimal import Decimal
import mapred

# Compute day of week performance as mean arrival delay.

def mapper(stream):
    fields = ['DayOfWeek', 'ArrDelay']
    dow_map = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

    for dow, delay in mapred.iter_curated_fields(stream, fields):
        dow_str = dow_map[int(dow) - 1]
        mapred.send(dow_str, (delay, 1))

def reducer(stream):
    dow_mean_delay = mapred.mean_accumulator_reducer(stream)

    for dow, mean_count in dow_mean_delay.iteritems():
        mapred.send(dow, mean_count)

def load_db(stream):
    import boto3
    dynamodb = boto3.resource('dynamodb', region_name='us-east-1')
    table = dynamodb.Table('ccproject_q1.3')
    with table.batch_writer() as batch:
        for dow, (delay, _) in mapred.iter_key_values(stream):
            batch.put_item(Item={
                'dummy_key': 1,
                'dow': dow,
                'delay': Decimal(delay),
            })


if __name__ == '__main__':
    if sys.argv[1] == 'map':
        mapper(sys.stdin)
    elif sys.argv[1] == 'reduce':
        reducer(sys.stdin)
    elif sys.argv[1] == 'load-db':
        load_db(sys.stdin)
