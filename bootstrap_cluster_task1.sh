rm ~/.ssh/known_hosts
ssh-keyscan -H 15.188.76.147 >> ~/.ssh/known_hosts
. venv/bin/activate
ansible-playbook -i ansible/inventory_task1 ansible/task1.yml
ansible-playbook -i ansible/inventory_task1 ansible/ccproject-setup-task1.yml
