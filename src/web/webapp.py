from operator import attrgetter
from collections import OrderedDict

from flask import Flask, redirect, url_for, request, render_template

from cassandra.cluster import Cluster
from cassandra.cqlengine import connection
from cassandra.cqlengine import columns
from cassandra.cqlengine.models import Model

cluster = Cluster(['node7', 'node8', 'node9'])
connection.register_connection('con', session=cluster.connect(), default=True)

app = Flask(__name__)

KEYSPACE = 'ccproject'

SITE = OrderedDict()

SITE['q1.1'] = {
    'query': "Top 10 most popular airports by numbers of flights to/from the airport.",
    'list_keys': ['airport', 'count'],
    'sort_by': 'count',
    'reverse': True,
    'template': 'list.html',
}
SITE['q1.2'] = {
    'query': "Top 10 airlines by on-time arrival performance.",
    'list_keys': ['carrier', 'delay'],
    'sort_by': 'delay',
    'template': 'list.html',
}
SITE['q1.3'] = {
    'query': "Days of the week ranked by on-time arrival performance.",
    'list_keys': ['dow', 'delay'],
    'sort_by': 'delay',
    'template': 'list.html',
}
SITE['q2.1'] = {
    'query': "Top 10 carriers by on-time departure performance from {origin}.",
    'params': ['origin'],
    'defaults': ['CMI'],
    'list_keys': ['carrier', 'delay'],
    'sort_by': 'delay',
    'template': 'list.html',
}
SITE['q2.2'] = {
    'query': "Top 10 airports by on-time departure performance from {origin}.",
    'params': ['origin'],
    'defaults': ['CMI'],
    'list_keys': ['dest', 'delay'],
    'sort_by': 'delay',
    'template': 'list.html',
}
SITE['q2.3'] = {
    'query': "Top 10 carrier by on-time departure performance at {origin} to {dest}.",
    'params': ['origin', 'dest'],
    'defaults': ['CMI', 'ORD'],
    'list_keys': ['carrier', 'delay'],
    'sort_by': 'delay',
    'template': 'list.html',
}
SITE['q2.4'] = {
    'query': "Mean arrival delay from {origin} to {dest}.",
    'params': ['origin', 'dest'],
    'defaults': ['CMI', 'ORD'],
    'template': 'q24.html',
}
SITE['q3'] = {
    'query': "Best trip on {date} from {origin} to {dest2} via a 2 days stop in {dest1}.",
    'params': ['date', 'origin', 'dest1', 'dest2'],
    'defaults': ['2008-03-04', 'CMI', 'ORD', 'LAX'],
    'parse_results': ['delay'],
    'template': 'q3.html',
}

@app.route('/', defaults={'path': 'q1.1'})
@app.route('/<path>', methods=['GET'])
def site(path):
    template = SITE[path]['template']
    nav = _get_nav(path)
    query = SITE[path]['query']
    params = SITE[path].get('params', [])
    defaults = SITE[path].get('defaults', [])
    query_args = SITE[path].get('query_args', {})

    param_values = OrderedDict()
    for param, default in zip(params, defaults):
        param_values[param] = request.args.get(param, default=default)

    query = query.format(**param_values)
    results = _request_cassandradb(path, param_values.copy(), query_args)

    if 'list_keys' in SITE[path]:
        sort_by = SITE[path]['sort_by']
        reverse = SITE[path].get('reverse', False)
        results = sorted(results, key=attrgetter(sort_by), reverse=reverse)[0:10]
        results = _parse_list(results, SITE[path]['list_keys'])
    else:
        results = results and results[0] or None

    return render_template(template, nav=nav, query=query, param_values=param_values, results=results)


def _request_cassandradb(path, params, query_args={}):
    model_path_map = {
        'q1.1': Q11Entry,
        'q1.2': Q12Entry,
        'q1.3': Q13Entry,
        'q2.1': Q21Entry,
        'q2.2': Q22Entry,
        'q2.3': Q23Entry,
        'q2.4': Q24Entry,
        'q3': Q3Entry,
    }
    model = model_path_map[path]

    if path == 'q3':
        query_args['dest2'] = params.pop('dest2')
    if params:
        key = '_'.join(key for key in params)
        key_value = '_'.join(value for value in params.itervalues())
        query_args[key] = key_value

    return model.objects.filter(**query_args)

def _parse_list(items, keys):
    results = []
    for item in items:
        values = tuple(item[key] for key in keys)
        results.append(values)
    return results


def _get_nav(active):
    nav = OrderedDict()
    for path in SITE:
        nav[path] = ''
    nav[active] = 'active'
    return nav


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
