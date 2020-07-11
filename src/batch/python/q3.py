#!/usr/bin/env python
import sys
from decimal import Decimal
import mapred

# Compute for all date/origin/dest1/dest2, the best flight with
# respect to total arrival delay, with the following criteria:
# - leg 2 departure date is date +2 days
# - leg 1, origin/dest1 is in the morning
# - leg 2, dest1/dest2 is in the afternoon
#
# Considering that the cardinality of origin/dest1/dest2 is quadratic, this problem does not
# scale well if we simply process the cartesian product of the inputs with itself. However, if
# we leverage the criteria, we can achieve linear complexity vs. the number of days and reduce
# the quadratic complexity vs. origin/destination:
#
#   - we only have to process the product of the records of a day, with the records in two days.
#   This is implemented with a specific InputFormat;
#   - we can go a bit further and implement the morning/afternoon criteria directly in the
#   InputFormat splitting.
#   - Finally, we can reduce the number of split by keeping the single best flight for each
#   origin destination in each morning/afternoon.
#
# Specific InputFormat and OutputFormat are used:
#
#   - KeySplitOutputFormat: which store each key in a key specific path. This is necessary
#   because the splitting stage must be aware of the date of each record.
#   - SuperSpecificCartesianInputFormat: this InputFormat implement the specific +2 days, morning
#   afternoon split by parsing thea path of each split and computing the requred cartesian
#   product.


def mapper_step1(stream):
    """This mapper produces the keys to be used for the first problem reduction step: selecting
    the single best flight for each day/day-part/origin/dest.
    """

    fields = ['FlightDate', 'Origin', 'Dest', 'ArrDelay', 'CRSDepTime', 'UniqueCarrier', 'FlightNum']

    for date, origin, dest, delay, time, carrier, flight in mapred.iter_curated_fields(stream, fields):
        try:
            minute_of_day = int(time[:2])*60 + int(time[2:])
        except:
            pass
        else:
            period = 'AM' if minute_of_day < 12*60 else 'PM'
            mapred.send((date, period), (date, origin, dest, delay, time, carrier, flight))

def reducer_step1(stream):
    """This implements the first reduction step. The output are keyed against day/day-part for the
    specific KeySplitOutputFormat to split the files.
    """

    least_delay_legs = {}
    for key, value in mapred.iter_key_values(stream):
        _, origin, dest, delay, _, _, _ = value
        leg_key = (key, origin, dest)
        if leg_key not in least_delay_legs:
            least_delay_legs[leg_key] = value
        else:
            _, _, _, best_delay, _, _, _ = least_delay_legs[leg_key]
            if float(delay) < float(best_delay):
                least_delay_legs[leg_key] = value

    for (key, _, _), value in least_delay_legs.iteritems():
        mapred.send(key, value)

def split_step1(stream, dst):
    import os
    filehandles = {}
    for line in stream:
        key, value = line.strip().split('\t')
        filepath = os.path.join(dst, key, 'part-00000')
        if filepath not in filehandles:
            parent = os.path.dirname(filepath)
            if not os.path.isdir(parent):
                os.makedirs(parent)
            filehandles[filepath] = open(filepath, 'w')
        filehandles[filepath].write(value + '\n')

    for fh in filehandles.itervalues():
        fh.close()

def mapper_step2(stream):
    """This mapper receives records from the SuperSpecificCartesianInputFormat, aka the cartesian product
    of the morning flights and +2 days afternoon flights. It simply have to reduce to the input where
    dest1 == origin2.
    """

    from cassandra.cluster import Cluster
    from cassandra.cqlengine import connection
    from decimal import Decimal

    from cassandra.cqlengine import columns
    from cassandra.cqlengine.models import Model

    from setup_cassandradb import Q3Entry

    cluster = Cluster(['node7', 'node8', 'node9'])
    connection.register_connection('con', session=cluster.connect(), default=True)

    for first_leg, second_leg in mapred.iter_key_values(stream):
        date1, origin1, dest1, delay1, time1, carrier1, flight1 = first_leg
        date2, origin2, dest2, delay2, time2, carrier2, flight2 = second_leg
        if dest1 == origin2:
            total_delay = float(delay1) + float(delay2)
            Q3Entry.create(
                date_origin_dest1 = '_'.join([date1, origin1, dest1]),
                date1 = date1,
                origin = origin1,
                dest1 = dest1,
                dest2 = dest2,
                total_delay = Decimal(total_delay),
                datetime1 = date1 + ' ' + time1,
                flight1 = carrier1 + ' ' + flight1,
                datetime2 = date2 + ' ' + time2,
                flight2 = carrier2 + ' ' + flight2
            )


if __name__ == '__main__':
    if sys.argv[1] == 'map-step1':
        mapper_step1(sys.stdin)
    elif sys.argv[1] == 'reduce-step1':
        reducer_step1(sys.stdin)
    elif sys.argv[1] == 'map-step2':
        mapper_step2(sys.stdin)
    elif sys.argv[1] == 'split-step1':
        split_step1(sys.stdin, sys.argv[2])
