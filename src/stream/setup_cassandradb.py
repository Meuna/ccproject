from cassandra.cluster import Cluster
from cassandra.cqlengine import connection
from cassandra.cqlengine.management import create_keyspace_simple, sync_table

from cassandra.cqlengine import columns
from cassandra.cqlengine.models import Model

KEYSPACE = 'ccproject'

class Q11Entry(Model):
    __keyspace__ = KEYSPACE
    airport = columns.Ascii(partition_key=True)
    count = columns.Integer()

class Q12Entry(Model):
    __keyspace__ = KEYSPACE
    carrier = columns.Ascii(partition_key=True)
    delay = columns.Decimal()

class Q13Entry(Model):
    __keyspace__ = KEYSPACE
    dow = columns.Ascii(partition_key=True)
    delay = columns.Decimal()

class Q21Entry(Model):
    __keyspace__ = KEYSPACE
    origin = columns.Ascii(partition_key=True)
    carrier = columns.Ascii(primary_key=True)
    delay = columns.Decimal()

class Q22Entry(Model):
    __keyspace__ = KEYSPACE
    origin = columns.Ascii(partition_key=True)
    dest = columns.Ascii(primary_key=True)
    delay = columns.Decimal()

class Q23Entry(Model):
    __keyspace__ = KEYSPACE
    origin_dest = columns.Ascii(partition_key=True)
    carrier = columns.Ascii(primary_key=True)
    delay = columns.Decimal()

class Q24Entry(Model):
    __keyspace__ = KEYSPACE
    origin_dest = columns.Ascii(partition_key=True)
    delay = columns.Decimal()

class Q3Entry(Model):
    __keyspace__ = KEYSPACE
    date_origin_dest1 = columns.Ascii(partition_key=True)
    date1 = columns.Ascii()
    origin = columns.Ascii()
    dest1 = columns.Ascii()
    dest2 = columns.Ascii(primary_key=True)
    total_delay = columns.Decimal()
    datetime1 = columns.Ascii()
    flight1 = columns.Ascii()
    datetime2 = columns.Ascii()
    flight2 = columns.Ascii()

if __name__ == '__main__':
    cluster = Cluster(['node7'])
    connection.register_connection('con', session=cluster.connect(), default=True)
    create_keyspace_simple(name=KEYSPACE, replication_factor=1, connections=['con'])

    for model in [Q11Entry, Q12Entry, Q13Entry, Q21Entry, Q22Entry, Q23Entry, Q24Entry, Q3Entry]:
        sync_table(model, connections=['con'])

    print("All tables created !")
