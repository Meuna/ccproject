import boto3
from botocore.exceptions import ClientError

table_q11 ={
    'TableName':'ccproject_q1.1',
    'KeySchema':[
        {
            'AttributeName': 'dummy_key',
            'KeyType': 'HASH'  #Partition key
        },
        {
            'AttributeName': 'count',
            'KeyType': 'RANGE'  #Sort key
        }
    ],
    'AttributeDefinitions':[
        {
            'AttributeName': 'dummy_key',
            'AttributeType': 'N'
        },
        {
            'AttributeName': 'count',
            'AttributeType': 'N'
        },
    ],
    'ProvisionedThroughput':{
        'ReadCapacityUnits': 10,
        'WriteCapacityUnits': 5
    }
}
table_q12 ={
    'TableName':'ccproject_q1.2',
    'KeySchema':[
        {
            'AttributeName': 'dummy_key',
            'KeyType': 'HASH'  #Partition key
        },
        {
            'AttributeName': 'delay',
            'KeyType': 'RANGE'  #Sort key
        }
    ],
    'AttributeDefinitions':[
        {
            'AttributeName': 'dummy_key',
            'AttributeType': 'N'
        },
        {
            'AttributeName': 'delay',
            'AttributeType': 'N'
        },
    ],
    'ProvisionedThroughput':{
        'ReadCapacityUnits': 10,
        'WriteCapacityUnits': 5
    }
}
table_q13 ={
    'TableName':'ccproject_q1.3',
    'KeySchema':[
        {
            'AttributeName': 'dummy_key',
            'KeyType': 'HASH'  #Partition key
        },
        {
            'AttributeName': 'delay',
            'KeyType': 'RANGE'  #Sort key
        }
    ],
    'AttributeDefinitions':[
        {
            'AttributeName': 'dummy_key',
            'AttributeType': 'N'
        },
        {
            'AttributeName': 'delay',
            'AttributeType': 'N'
        },
    ],
    'ProvisionedThroughput':{
        'ReadCapacityUnits': 10,
        'WriteCapacityUnits': 5
    }
}

table_confs = [table_q11, table_q12, table_q13]

if __name__ == '__main__':
    dynamodb = boto3.resource('dynamodb', region_name='us-east-1')

    for conf in table_confs:
        try:
            table = dynamodb.create_table(**conf)
        except ClientError as e:
            error_code = e.response.get("Error", {}).get("Code")
            if error_code == "ResourceInUseException":
                print("Table {TableName} already exists".format(**conf))
            else:
                raise

    client = boto3.client('dynamodb', region_name='us-east-1')
    for conf in table_confs:
        client.get_waiter('table_exists').wait(TableName=conf['TableName'])

    print("All tables created !")
