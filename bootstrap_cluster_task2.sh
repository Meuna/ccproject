rm ~/.ssh/known_hosts
ssh-keyscan -H 52.47.91.93 >> ~/.ssh/known_hosts
. venv/bin/activate
ansible-playbook -i ansible/inventory_task2 ansible/task2.yml
ansible-playbook -i ansible/inventory_task2 ansible/ccproject-setup-task2.yml
