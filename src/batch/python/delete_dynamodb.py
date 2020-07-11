import boto3

table_names = [
    'ccproject_q1.1', 'ccproject_q1.2', 'ccproject_q1.3',
]

if __name__ == '__main__':
    client = boto3.client('dynamodb', region_name='us-east-1')

    for table_name in table_names:
        client.delete_table(TableName=table_name)
