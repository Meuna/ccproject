import boto3
from boto3.dynamodb.conditions import Key, Attr

from cassandra.cluster import Cluster
from cassandra.cqlengine import connection

from setup_cassandradb import Q21Entry, Q22Entry, Q23Entry, Q24Entry, Q3Entry

REGION_NAME = 'us-east-1'

q21_queries = ['CMI', 'BWI', 'MIA', 'LAX', 'IAH', 'SFO']
q22_queries = ['CMI', 'BWI', 'MIA', 'LAX', 'IAH', 'SFO']
q23_queries = ['CMI_ORD', 'IND_CMH', 'DFW_IAH', 'LAX_SFO', 'JFK_LAX', 'ATL_PHX']
q24_queries = ['CMI_ORD', 'IND_CMH', 'DFW_IAH', 'LAX_SFO', 'JFK_LAX', 'ATL_PHX']
q3_queries = [
    ('2008-03-04_CMI_ORD', 'LAX'),
    ('2008-09-09_JAX_DFW', 'CRP'),
    ('2008-04-01_SLC_BFL', 'LAX'),
    ('2008-07-12_LAX_SFO', 'PHX'),
    ('2008-06-10_DFW_ORD', 'DFW'),
    ('2008-01-01_LAX_ORD', 'JFK'),
]

if __name__ == '__main__':
    dynamodb = boto3.resource('dynamodb', region_name=REGION_NAME)

    cluster = Cluster(['node7', 'node8', 'node9'])
    connection.register_connection('con', session=cluster.connect(), default=True)

    query_args = {
        'KeyConditionExpression': Key('dummy_key').eq(1),
        'Limit': 10,
    }

    # q1.1
    print("*** Q1.1 ***")
    table = dynamodb.Table('ccproject_q1.1')
    response = table.query(ScanIndexForward=False, **query_args)
    for item in response['Items']:
        print('{airport}\t{count}'.format(**item))

    # q1.2
    print("*** Q1.2 ***")
    table = dynamodb.Table('ccproject_q1.2')
    response = table.query(**query_args)
    for item in response['Items']:
        print('{carrier}\t{delay:.3f}'.format(**item))

    # q1.2
    print("*** Q1.3 ***")
    table = dynamodb.Table('ccproject_q1.3')
    response = table.query(**query_args)
    for item in response['Items']:
        print('{dow}\t{delay:.3f}'.format(**item))

    # q2.1
    print("*** Q2.1 ***")
    for origin in q21_queries:
        print("Query: {}".format(origin))
        response = Q21Entry.objects.filter(origin=origin).limit(10)
        for item in response:
            print('{carrier}\t{delay:.3f}'.format(**item))

    # q2.2
    print("*** Q2.2 ***")
    for origin in q22_queries:
        print("Query: {}".format(origin))
        response = Q22Entry.objects.filter(origin=origin).limit(10)
        for item in response:
            print('{dest}\t{delay:.3f}'.format(**item))

    # q2.3
    print("*** Q2.3 ***")
    for origin_dest in q23_queries:
        print("Query: {}".format(origin_dest))
        response = Q23Entry.objects.filter(origin_dest=origin_dest).limit(10)
        for item in response:
            print('{carrier}\t{delay:.3f}'.format(**item))

    # q2.4
    print("*** Q2.4 ***")
    for origin_dest in q24_queries:
        print("Query: {}".format(origin_dest))
        response = Q24Entry.objects.filter(origin_dest=origin_dest)
        if response:
            print(response[0]['delay'])

    # q2.4
    print("*** Q3 ***")
    for date_origin_dest1, dest2 in q3_queries:
        print("Query: {}_{}".format(date_origin_dest1, dest2))
        response = Q3Entry.objects.filter(date_origin_dest1=date_origin_dest1, dest2=dest2)
        if response:
            print('Totaldelay: {total_delay:.3f}'.format(**response[0]))
            print('Leg 1: {origin} > {dest1}, {datetime1} {flight1}'.format(**response[0]))
            print('Leg 2: {dest1} > {dest2}, {datetime2} {flight2}'.format(**response[0]))
