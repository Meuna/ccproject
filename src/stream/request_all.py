from operator import attrgetter

from cassandra.cluster import Cluster
from cassandra.cqlengine import connection

from setup_cassandradb import Q11Entry, Q12Entry, Q13Entry, Q21Entry, Q22Entry, Q23Entry, Q24Entry, Q3Entry


q21_queries = ['SRQ', 'CMH', 'JFK', 'SEA', 'BOS']
q22_queries = ['SRQ', 'CMH', 'JFK', 'SEA', 'BOS']
q23_queries = ['LGA_BOS', 'BOS_LGA', 'OKC_DFW', 'MSP_ATL']
q24_queries = ['LGA_BOS', 'BOS_LGA', 'OKC_DFW', 'MSP_ATL']
q3_queries = [
    ('2008-04-03_BOS_ATL', 'LAX'),
    ('2008-09-07_PHX_JFK', 'MSP'),
    ('2008-01-24_DFW_STL', 'ORD'),
    ('2008-05-16_LAX_MIA', 'LAX'),
]

if __name__ == '__main__':
    cluster = Cluster(['node7', 'node8', 'node9'])
    connection.register_connection('con', session=cluster.connect(), default=True)

    # q1.1
    print("*** Q1.1 ***")
    response = Q11Entry.objects
    response = sorted(response, key=attrgetter('count'), reverse=True)[0:10]
    for item in response:
        print('{airport}\t{count}'.format(**item))

    # q1.2
    print("*** Q1.2 ***")
    response = Q12Entry.objects
    response = sorted(response, key=attrgetter('delay'))[0:10]
    for item in response:
        print('{carrier}\t{delay:.3f}'.format(**item))

    # q1.2
    print("*** Q1.3 ***")
    response = Q13Entry.objects
    response = sorted(response, key=attrgetter('delay'))
    for item in response:
        print('{dow}\t{delay:.3f}'.format(**item))

    # q2.1
    print("*** Q2.1 ***")
    for origin in q21_queries:
        print("Query: {}".format(origin))
        response = Q21Entry.objects.filter(origin=origin)
        response = sorted(response, key=attrgetter('delay'))[0:10]
        for item in response:
            print('{carrier}\t{delay:.3f}'.format(**item))

    # q2.2
    print("*** Q2.2 ***")
    for origin in q22_queries:
        print("Query: {}".format(origin))
        response = Q22Entry.objects.filter(origin=origin)
        response = sorted(response, key=attrgetter('delay'))[0:10]
        for item in response:
            print('{dest}\t{delay:.3f}'.format(**item))

    # q2.3
    print("*** Q2.3 ***")
    for origin_dest in q23_queries:
        print("Query: {}".format(origin_dest))
        response = Q23Entry.objects.filter(origin_dest=origin_dest)
        response = sorted(response, key=attrgetter('delay'))[0:10]
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
