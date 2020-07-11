#!/usr/bin/env python
import sys
import mapred

# Compute airport popularity as number of flight inboud and outboud.

def mapper(stream):
    fields = ['Origin', 'Dest']

    for origin, dest in mapred.iter_curated_fields(stream, fields):
        mapred.send(origin, 1)
        mapred.send(dest, 1)

def reducer(stream):
    airport_acc = {}
    for airport, count in mapred.iter_key_values(stream):
        airport_acc[airport] = airport_acc.get(airport, 0) + int(count)

    for airport, count in airport_acc.iteritems():
        mapred.send(airport, count)

def load_db(stream):
    import boto3
    dynamodb = boto3.resource('dynamodb', region_name='us-east-1')
    table = dynamodb.Table('ccproject_q1.1')
    with table.batch_writer() as batch:
        for airport, count in mapred.iter_key_values(stream):
            batch.put_item(Item={
                'dummy_key': 1,
                'airport': airport,
                'count': int(count),
            })

if __name__ == '__main__':
    if sys.argv[1] == 'map':
        mapper(sys.stdin)
    elif sys.argv[1] == 'reduce':
        reducer(sys.stdin)
    elif sys.argv[1] == 'load-db':
        load_db(sys.stdin)
