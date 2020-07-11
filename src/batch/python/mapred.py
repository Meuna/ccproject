
SEP = '\t'
SECONDARY_SEP = '__'

AVIATION_LINE_FORMAT = [
    "Year","Quarter","Month","DayofMonth","DayOfWeek","FlightDate","UniqueCarrier",
    "AirlineID","Carrier","TailNum","FlightNum","Origin","OriginCityName","OriginState",
    "OriginStateFips","OriginStateName","OriginWac","Dest","DestCityName","DestState",
    "DestStateFips","DestStateName","DestWac","CRSDepTime","DepTime","DepDelay",
    "DepDelayMinutes","DepDel15","DepartureDelayGroups","DepTimeBlk","TaxiOut",
    "WheelsOff","WheelsOn","TaxiIn","CRSArrTime","ArrTime","ArrDelay","ArrDelayMinutes",
    "ArrDel15","ArrivalDelayGroups","ArrTimeBlk","Cancelled","CancellationCode",
    "Diverted","CRSElapsedTime","ActualElapsedTime","AirTime","Flights","Distance",
    "DistanceGroup","CarrierDelay","WeatherDelay","NASDelay","SecurityDelay",
    "LateAircraftDelay"
]

def iter_curated_fields(stream, fields):
    fields_id = [AVIATION_LINE_FORMAT.index(field) for field in fields]
    max_field = max(fields_id)

    for line in stream:
        # We ignore header lines
        if not line.startswith('"Year"'):
            # The line bellow remove the comma in CityName, which break line.split(',')
            line = line.strip().replace(', ', '_')
            fields = line.split(',')
            # We ignore lines missing a field
            if len(fields) > max_field:
                field_values = [fields[id].replace('"', '') for id in fields_id]
                # And we finally ignore lines with any empty fields
                if all(field_values):
                    yield field_values

def send(key, value):
    # Packing of multi-key/multi-values
    if isinstance(key, (tuple, list)):
        key = SECONDARY_SEP.join(str(item) for item in key)
    if isinstance(value, (tuple, list)):
        value = SECONDARY_SEP.join(str(item) for item in value)

    # Sending key/value
    print(str(key) + SEP + str(value))

def iter_key_values(stream):
    for line in stream:
        key, value = line.strip().split(SEP)
        key = tuple(key.split(SECONDARY_SEP))
        value = tuple(value.split(SECONDARY_SEP))
        if len(key) == 1:
            key = key[0]
        if len(value) == 1:
            value = value[0]
        yield key, value

def mean_accumulator_reducer(stream):
    # This structure map keys with the accumulated mean and
    # sample count in a tuple: acc_by_key[key] = (mean, count)
    acc_by_key = {}

    for key, (mean, count) in iter_key_values(stream):
        # Multi values are simply split
        mean = float(mean)
        count = int(count)

        if key not in acc_by_key:
            acc_by_key[key] = (0.0, 0)
        acc_mean, acc_count = acc_by_key[key]

        # Accumulate the new samples into the accumulator
        acc_mean = (acc_mean*acc_count + mean*count)/(acc_count + count)
        acc_count = acc_count + count

        acc_by_key[key] = (acc_mean, acc_count)

    return acc_by_key
